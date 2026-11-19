package com.stocktag.service;

import com.stocktag.model.Empresa;
import com.stocktag.model.Producto;
import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.EmpresaResumen;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de EmpresaService con repositorios reales sobre H2 en memoria
 * ({@code @DataJpaTest} no toca ./data). Cada prueba se revierte al terminar.
 */
@DataJpaTest
@Import({EmpresaService.class, EmpresaServiceTest.Config.class})
@RecordApplicationEvents
class EmpresaServiceTest {

    @TestConfiguration
    static class Config {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEvents eventos;

    // --- registrarEmpresa ------------------------------------------------------------

    @Test
    void registrarEmpresaCreaLaEmpresaActivaYSuUsuarioConClaveBcrypt() {
        Empresa empresa = empresaService.registrarEmpresa(
                "  Ferretería El Tornillo ", "900.111.222-1", " tornillo ", "clave-segura");

        Empresa guardada = empresaRepository.findById(empresa.getId()).orElseThrow();
        assertThat(guardada.getNombre()).isEqualTo("Ferretería El Tornillo");
        assertThat(guardada.getNit()).isEqualTo("900111222-1"); // sin puntos ni espacios
        assertThat(guardada.getFechaAlta()).isEqualTo(LocalDate.now());
        assertThat(guardada.isActiva()).isTrue();

        Usuario usuario = usuarioRepository.findByUsername("tornillo").orElseThrow();
        assertThat(usuario.getRol()).isEqualTo(Rol.EMPRESA);
        assertThat(usuario.getEmpresa().getId()).isEqualTo(empresa.getId());
        assertThat(usuario.getPassword()).isNotEqualTo("clave-segura").startsWith("$2");
        assertThat(passwordEncoder.matches("clave-segura", usuario.getPassword())).isTrue();
    }

    @Test
    void nitRepetidoLanzaErrorEnEspanolYNoCreaNada() {
        empresaService.registrarEmpresa("Papelería La Hoja", "900333444-2", "lahoja", "clave-segura");
        long empresasAntes = empresaRepository.count();
        long usuariosAntes = usuarioRepository.count();

        // Mismo NIT escrito con puntos: se normaliza y se detecta igual.
        assertThatThrownBy(() -> empresaService.registrarEmpresa(
                "Otra Papelería", "900.333.444-2", "otra", "clave-segura"))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("Ya existe una empresa registrada con el NIT 900333444-2.")
                .extracting("campo").isEqualTo("nit");

        assertThat(empresaRepository.count()).isEqualTo(empresasAntes);
        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        assertThat(usuarioRepository.findByUsername("otra")).isEmpty();
    }

    @Test
    void usuarioRepetidoLanzaErrorEnEspanolYNoCreaLaEmpresa() {
        empresaService.registrarEmpresa("Papelería La Hoja", "900333444-2", "lahoja", "clave-segura");
        long empresasAntes = empresaRepository.count();

        // Mayúsculas distintas cuentan como el mismo usuario.
        assertThatThrownBy(() -> empresaService.registrarEmpresa(
                "Tienda Nueva", "900999888-7", "LaHoja", "clave-segura"))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("El usuario \"LaHoja\" ya está en uso. Elige otro.")
                .extracting("campo").isEqualTo("username");

        assertThat(empresaRepository.count()).isEqualTo(empresasAntes);
        assertThat(empresaRepository.existsByNit("900999888-7")).isFalse();
    }

    @Test
    void claveDeMenosDeOchoCaracteresSeRechaza() {
        assertThatThrownBy(() -> empresaService.registrarEmpresa("Tienda", "900123123-1", "tienda1", "1234567"))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("La contraseña debe tener al menos 8 caracteres.")
                .extracting("campo").isEqualTo("password");
        assertThat(empresaRepository.existsByNit("900123123-1")).isFalse();
    }

    @Test
    void datosObligatoriosVaciosSeRechazan() {
        assertThatThrownBy(() -> empresaService.registrarEmpresa(" ", "900123123-1", "tienda1", "clave-segura"))
                .isInstanceOf(EmpresaException.class).extracting("campo").isEqualTo("nombre");
        assertThatThrownBy(() -> empresaService.registrarEmpresa("Tienda", "", "tienda1", "clave-segura"))
                .isInstanceOf(EmpresaException.class).extracting("campo").isEqualTo("nit");
        assertThatThrownBy(() -> empresaService.registrarEmpresa("Tienda", "900123123-1", null, "clave-segura"))
                .isInstanceOf(EmpresaException.class).extracting("campo").isEqualTo("username");
    }

    // --- cambiarEstadoEmpresa --------------------------------------------------------

    @Test
    void desactivarYActivarCambiaElEstadoYSoloDesactivarPublicaElEvento() {
        Empresa empresa = empresaService.registrarEmpresa("Tienda Sol", "900444555-3", "tiendasol", "clave-segura");

        empresaService.cambiarEstadoEmpresa(empresa.getId(), false);
        assertThat(empresaRepository.findById(empresa.getId()).orElseThrow().isActiva()).isFalse();
        assertThat(eventos.stream(EmpresaDesactivadaEvent.class))
                .containsExactly(new EmpresaDesactivadaEvent(empresa.getId()));

        eventos.clear();
        empresaService.cambiarEstadoEmpresa(empresa.getId(), true);
        assertThat(empresaRepository.findById(empresa.getId()).orElseThrow().isActiva()).isTrue();
        assertThat(eventos.stream(EmpresaDesactivadaEvent.class)).isEmpty();
    }

    @Test
    void cambiarEstadoDeEmpresaInexistenteLanzaError() {
        assertThatThrownBy(() -> empresaService.cambiarEstadoEmpresa(999_999L, false))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("La empresa indicada no existe.");
        assertThat(eventos.stream(EmpresaDesactivadaEvent.class)).isEmpty();
    }

    // --- restablecerClave -----------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN_PLATAFORMA")
    void restablecerClaveGuardaLaNuevaConBcryptYPideCerrarLasSesiones() {
        Empresa empresa = empresaService.registrarEmpresa("Tienda Clave", "900777888-1", "tiendaclave", "clave-vieja");

        Usuario usuario = empresaService.restablecerClave(empresa.getId(), "clave-nueva-123");

        assertThat(usuario.getUsername()).isEqualTo("tiendaclave");
        String hash = usuarioRepository.findByUsername("tiendaclave").orElseThrow().getPassword();
        assertThat(hash).startsWith("$2").isNotEqualTo("clave-nueva-123");
        assertThat(passwordEncoder.matches("clave-nueva-123", hash)).isTrue();
        assertThat(passwordEncoder.matches("clave-vieja", hash)).isFalse();
        assertThat(eventos.stream(ClaveRestablecidaEvent.class))
                .containsExactly(new ClaveRestablecidaEvent("tiendaclave"));
    }

    @Test
    @WithMockUser(roles = "ADMIN_PLATAFORMA")
    void restablecerClaveRechazaClavesInvalidasYConservaLaAnterior() {
        Empresa empresa = empresaService.registrarEmpresa("Tienda Valida", "900777999-2", "tiendavalida", "clave-vieja");

        assertThatThrownBy(() -> empresaService.restablecerClave(empresa.getId(), "1234567"))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("La contraseña debe tener al menos 8 caracteres.")
                .extracting("campo").isEqualTo("password");
        assertThatThrownBy(() -> empresaService.restablecerClave(empresa.getId(), "         "))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("La contraseña no puede ser solo espacios.");
        // 40 eñes son 40 caracteres pero 80 bytes: BCrypt solo usaría los primeros 72.
        assertThatThrownBy(() -> empresaService.restablecerClave(empresa.getId(), "ñ".repeat(40)))
                .isInstanceOf(EmpresaException.class)
                .hasMessageStartingWith("La contraseña es demasiado larga");
        assertThatThrownBy(() -> empresaService.restablecerClave(empresa.getId(), null))
                .isInstanceOf(EmpresaException.class)
                .extracting("campo").isEqualTo("password");

        String hash = usuarioRepository.findByUsername("tiendavalida").orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("clave-vieja", hash)).isTrue();
        assertThat(eventos.stream(ClaveRestablecidaEvent.class)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN_PLATAFORMA")
    void restablecerClaveDeEmpresaInexistenteLanzaError() {
        assertThatThrownBy(() -> empresaService.restablecerClave(999_999L, "clave-nueva-123"))
                .isInstanceOf(EmpresaException.class)
                .hasMessage("La empresa indicada no existe.")
                .extracting("campo").isNull();
        assertThat(eventos.stream(ClaveRestablecidaEvent.class)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "EMPRESA")
    void unaEmpresaNoPuedeRestablecerContrasenas() {
        Empresa empresa = empresaService.registrarEmpresa("Tienda Ajena", "900777000-3", "tiendaajena", "clave-vieja");

        assertThatThrownBy(() -> empresaService.restablecerClave(empresa.getId(), "clave-nueva-123"))
                .isInstanceOf(AccessDeniedException.class);

        String hash = usuarioRepository.findByUsername("tiendaajena").orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("clave-vieja", hash)).isTrue();
    }

    @Test
    @WithAnonymousUser
    void sinIniciarSesionNoSePuedeRestablecerContrasenas() {
        Empresa empresa = empresaService.registrarEmpresa("Tienda Anonima", "900777111-4", "tiendaanonima", "clave-vieja");

        assertThatThrownBy(() -> empresaService.restablecerClave(empresa.getId(), "clave-nueva-123"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- listarEmpresas --------------------------------------------------------------

    @Test
    void listarEmpresasTraeUsuarioYNumeroDeProductosOrdenadoPorNombre() {
        Empresa zapateria = empresaService.registrarEmpresa("Zapatería Paso", "900000010-1", "paso", "clave-segura");
        Empresa abarrotes = empresaService.registrarEmpresa("abarrotes Don Luis", "900000020-2", "donluis", "clave-segura");
        productoRepository.save(new Producto("1", "Arroz", 1, abarrotes));
        productoRepository.save(new Producto("2", "Frijol", 1, abarrotes));
        empresaService.cambiarEstadoEmpresa(zapateria.getId(), false);

        List<EmpresaResumen> empresas = empresaService.listarEmpresas();

        assertThat(empresas).extracting(EmpresaResumen::nombre)
                .containsExactly("abarrotes Don Luis", "Zapatería Paso");
        EmpresaResumen primera = empresas.get(0);
        assertThat(primera.username()).isEqualTo("donluis");
        assertThat(primera.nit()).isEqualTo("900000020-2");
        assertThat(primera.totalProductos()).isEqualTo(2L);
        assertThat(primera.activa()).isTrue();
        assertThat(primera.fechaAlta()).isEqualTo(LocalDate.now());
        assertThat(empresas.get(1).totalProductos()).isZero();
        assertThat(empresas.get(1).activa()).isFalse();
    }
}
