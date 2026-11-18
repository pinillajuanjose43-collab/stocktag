package com.stocktag.security;

import com.stocktag.model.Empresa;
import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de UsuarioDetailsService (carga de usuarios para el login) con repositorios reales
 * sobre H2 en memoria. Cada prueba se revierte al terminar.
 */
@DataJpaTest
@Import(UsuarioDetailsService.class)
class UsuarioDetailsServiceTest {

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Test
    void usuarioDeEmpresaActivaQuedaHabilitadoConSuEmpresa() {
        Empresa empresa = empresaRepository.save(new Empresa("Papelería Central", "900111222-3"));
        usuarioRepository.save(new Usuario("papeleria", "clave-cifrada", Rol.EMPRESA, empresa));

        UserDetails detalles = usuarioDetailsService.loadUserByUsername("papeleria");

        assertThat(detalles).isInstanceOf(UsuarioAutenticado.class);
        UsuarioAutenticado usuario = (UsuarioAutenticado) detalles;
        assertThat(usuario.isEnabled()).isTrue();
        assertThat(usuario.getEmpresaId()).isEqualTo(empresa.getId());
        assertThat(usuario.getNombreEmpresa()).isEqualTo("Papelería Central");
        assertThat(usuario.getAuthorities()).extracting("authority").containsExactly("ROLE_EMPRESA");
    }

    @Test
    void usuarioDeEmpresaDesactivadaQuedaDeshabilitado() {
        Empresa empresa = new Empresa("Ferretería El Tornillo", "900333444-5");
        empresa.setActiva(false);
        empresaRepository.save(empresa);
        usuarioRepository.save(new Usuario("ferreteria", "clave-cifrada", Rol.EMPRESA, empresa));

        assertThat(usuarioDetailsService.loadUserByUsername("ferreteria").isEnabled()).isFalse();
    }

    @Test
    void administradorNoTieneEmpresaYQuedaHabilitado() {
        usuarioRepository.save(new Usuario("superadmin", "clave-cifrada", Rol.ADMIN_PLATAFORMA, null));

        UsuarioAutenticado admin = (UsuarioAutenticado) usuarioDetailsService.loadUserByUsername("superadmin");

        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getEmpresaId()).isNull();
        assertThat(admin.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN_PLATAFORMA");
    }

    @Test
    void usuarioInexistenteLanzaExcepcion() {
        assertThatThrownBy(() -> usuarioDetailsService.loadUserByUsername("nadie"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
