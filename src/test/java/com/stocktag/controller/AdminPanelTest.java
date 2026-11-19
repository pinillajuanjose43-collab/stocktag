package com.stocktag.controller;

import com.stocktag.model.Empresa;
import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.UsuarioRepository;
import com.stocktag.security.UsuarioAutenticado;
import com.stocktag.service.EmpresaService;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pruebas del panel /admin, del cierre de sesiones al desactivar una empresa y de la separación
 * de roles. H2 en memoria (no toca ./data).
 *
 * <p>Sin {@code @Transactional}: el cierre de sesiones ocurre después del commit, así que cada
 * prueba confirma sus datos de verdad. Por eso cada una usa NIT y usuario únicos.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocktag-test;DB_CLOSE_DELAY=-1",
        "stocktag.admin.username=admin",
        "stocktag.admin.password=admin123"
})
@AutoConfigureMockMvc
class AdminPanelTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final String CLAVE = "clave-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // --- Tabla de empresas -----------------------------------------------------------

    @Test
    void adminVeLaTablaConLosDatosYElEstadoDeCadaEmpresa() throws Exception {
        Datos activa = registrar("Ferretería Tabla");
        Datos desactivada = registrar("Papelería Tabla");
        empresaService.cambiarEstadoEmpresa(desactivada.empresa().getId(), false);

        mockMvc.perform(get("/admin").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Ferretería Tabla"),
                        containsString(activa.empresa().getNit()),
                        containsString(activa.username()),
                        containsString("Activa"),
                        containsString("Desactivada"),
                        containsString("Desactivar Ferretería Tabla"),
                        containsString("/admin/empresas/" + desactivada.empresa().getId() + "/activar"),
                        containsString("Nueva empresa"))));
    }

    @Test
    void desactivarPrimeroPideConfirmacionEnLaMismaPaginaSinJavaScript() throws Exception {
        Datos datos = registrar("Tienda Confirmar");
        Long id = datos.empresa().getId();

        // Sin ?confirmar no hay panel de confirmación.
        mockMvc.perform(get("/admin").with(admin()))
                .andExpect(content().string(not(containsString("id=\"confirmar-desactivar\""))));

        mockMvc.perform(get("/admin").param("confirmar", id.toString()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"confirmar-desactivar\""),
                        containsString("¿Desactivar Tienda Confirmar?"),
                        containsString("action=\"/admin/empresas/" + id + "/desactivar\""),
                        containsString("Sí, desactivar"),
                        containsString("Cancelar"),
                        not(containsString("alert(")),
                        not(containsString("confirm(")))));

        // Aún no cambió nada.
        assertThat(empresaRepository.findById(id).orElseThrow().isActiva()).isTrue();
    }

    @Test
    void confirmarDesactivaLaEmpresaYMuestraMensajeDeExito() throws Exception {
        Datos datos = registrar("Tienda Desactivar");
        Long id = datos.empresa().getId();

        mockMvc.perform(post("/admin/empresas/{id}/desactivar", id).with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("exito",
                        "Empresa \"Tienda Desactivar\" desactivada. Su usuario perdió el acceso, incluida cualquier sesión abierta."));
        assertThat(empresaRepository.findById(id).orElseThrow().isActiva()).isFalse();

        mockMvc.perform(post("/admin/empresas/{id}/activar", id).with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("exito",
                        "Empresa \"Tienda Desactivar\" activada. Su usuario ya puede iniciar sesión."));
        assertThat(empresaRepository.findById(id).orElseThrow().isActiva()).isTrue();
    }

    @Test
    void desactivarEmpresaInexistenteMuestraError() throws Exception {
        mockMvc.perform(post("/admin/empresas/{id}/desactivar", 987_654L).with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", "La empresa indicada no existe."));
    }

    // --- Formulario de nueva empresa -------------------------------------------------

    @Test
    void formularioMuestraLosCuatroCamposYLaConfirmacion() throws Exception {
        mockMvc.perform(get("/admin/empresas/nueva").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("name=\"nombre\""),
                        containsString("name=\"nit\""),
                        containsString("name=\"username\""),
                        containsString("name=\"password\""),
                        containsString("name=\"confirmacion\""),
                        containsString("autocomplete=\"new-password\""))));
    }

    @Test
    void registrarDesdeElFormularioCreaLaEmpresaYRedirigeConMensaje() throws Exception {
        int n = SECUENCIA.incrementAndGet();
        String username = "form" + n;

        mockMvc.perform(post("/admin/empresas").with(admin()).with(csrf())
                        .param("nombre", "Droguería Formulario")
                        .param("nit", "600." + n + "-1")
                        .param("username", username)
                        .param("password", CLAVE)
                        .param("confirmacion", CLAVE))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("exito",
                        "Empresa \"Droguería Formulario\" registrada. Ya puede iniciar sesión con el usuario \"" + username + "\"."));

        Usuario usuario = usuarioRepository.findByUsername(username).orElseThrow();
        assertThat(usuario.getRol()).isEqualTo(Rol.EMPRESA);
        assertThat(usuario.getEmpresa().getNit()).isEqualTo("600" + n + "-1");
    }

    @Test
    void erroresDeValidacionSeMuestranDebajoDeCadaCampoYNoSeDevuelvenLasClaves() throws Exception {
        mockMvc.perform(post("/admin/empresas").with(admin()).with(csrf())
                        .param("nombre", "")
                        .param("nit", "abc")
                        .param("username", "con espacio")
                        .param("password", "corta")
                        .param("confirmacion", "otra"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/empresa-form"))
                .andExpect(content().string(allOf(
                        containsString("id=\"nombre-error\""),
                        containsString("Escribe el nombre de la empresa."),
                        containsString("id=\"nit-error\""),
                        containsString("Escribe el NIT con números"),
                        containsString("id=\"username-error\""),
                        containsString("Usa de 3 a 30 caracteres"),
                        containsString("id=\"password-error\""),
                        containsString("La contraseña debe tener entre 8 y 72 caracteres."),
                        containsString("id=\"confirmacion-error\""),
                        containsString("Las contraseñas no coinciden."),
                        not(containsString("value=\"corta\"")),
                        not(containsString("value=\"otra\"")))));
    }

    @Test
    void confirmacionDistintaSeMuestraDebajoDeLaConfirmacion() throws Exception {
        mockMvc.perform(post("/admin/empresas").with(admin()).with(csrf())
                        .param("nombre", "Tienda Claves")
                        .param("nit", "600999-1")
                        .param("username", "claves")
                        .param("password", CLAVE)
                        .param("confirmacion", CLAVE + "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"confirmacion-error\""),
                        containsString("Las contraseñas no coinciden."),
                        not(containsString("id=\"password-error\"")))));
        assertThat(usuarioRepository.findByUsername("claves")).isEmpty();
    }

    @Test
    void nitYUsuarioRepetidosSeMuestranDebajoDeSuCampo() throws Exception {
        Datos existente = registrar("Tienda Existente");

        mockMvc.perform(post("/admin/empresas").with(admin()).with(csrf())
                        .param("nombre", "Tienda Copia")
                        .param("nit", existente.empresa().getNit())
                        .param("username", "copia" + SECUENCIA.incrementAndGet())
                        .param("password", CLAVE)
                        .param("confirmacion", CLAVE))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"nit-error\""),
                        containsString("Ya existe una empresa registrada con el NIT " + existente.empresa().getNit() + "."))));

        mockMvc.perform(post("/admin/empresas").with(admin()).with(csrf())
                        .param("nombre", "Tienda Copia")
                        .param("nit", "600" + SECUENCIA.incrementAndGet() + "-5")
                        .param("username", existente.username().toUpperCase())
                        .param("password", CLAVE)
                        .param("confirmacion", CLAVE))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"username-error\""),
                        containsString("ya está en uso. Elige otro."))));
    }

    // --- Empresa desactivada: login y sesión abierta ---------------------------------

    @Test
    void empresaDesactivadaNoPuedeIniciarSesion() throws Exception {
        Datos datos = registrar("Tienda Sin Login");
        mockMvc.perform(formLogin("/login").user(datos.username()).password(CLAVE))
                .andExpect(redirectedUrl("/"));

        empresaService.cambiarEstadoEmpresa(datos.empresa().getId(), false);

        mockMvc.perform(formLogin("/login").user(datos.username()).password(CLAVE))
                .andExpect(redirectedUrl("/login?desactivada"));
    }

    @Test
    void sesionAbiertaPierdeElAccesoEnLaSiguientePeticionAlDesactivarLaEmpresa() throws Exception {
        Datos datos = registrar("Tienda Con Sesion");
        Datos otra = registrar("Tienda Vecina");
        MockHttpSession sesion = iniciarSesion(datos.username());
        MockHttpSession sesionOtra = iniciarSesion(otra.username());

        mockMvc.perform(get("/productos").session(sesion)).andExpect(status().isOk());

        empresaService.cambiarEstadoEmpresa(datos.empresa().getId(), false);

        // La siguiente petición de esa sesión ya no entra: se cierra y va al login con el aviso.
        mockMvc.perform(get("/productos").session(sesion))
                .andExpect(redirectedUrl("/login?desactivada"));
        assertThat(sesion.isInvalid()).isTrue();

        // La sesión de otra empresa no se ve afectada.
        mockMvc.perform(get("/productos").session(sesionOtra)).andExpect(status().isOk());
    }

    @Test
    void elLoginMuestraElAvisoDeEmpresaDesactivada() throws Exception {
        mockMvc.perform(get("/login").param("desactivada", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu empresa está desactivada")));
    }

    // --- Separación de roles ---------------------------------------------------------

    @Test
    void empresaNoPuedeEntrarAlPanelAdmin() throws Exception {
        Datos datos = registrar("Tienda Curiosa");
        RequestPostProcessor empresa = user(new UsuarioAutenticado(
                usuarioRepository.findByUsername(datos.username()).orElseThrow()));

        mockMvc.perform(get("/admin").with(empresa)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/empresas/nueva").with(empresa)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/empresas").with(empresa).with(csrf())
                        .param("nombre", "Intrusa").param("nit", "600123-1").param("username", "intrusa")
                        .param("password", CLAVE).param("confirmacion", CLAVE))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/empresas/{id}/desactivar", datos.empresa().getId()).with(empresa).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByUsername("intrusa")).isEmpty();
        assertThat(empresaRepository.findById(datos.empresa().getId()).orElseThrow().isActiva()).isTrue();
    }

    @Test
    void adminNoVeInventarios() throws Exception {
        for (String ruta : new String[]{"/productos", "/productos/nuevo", "/alertas", "/movimientos", "/reporte.csv"}) {
            mockMvc.perform(get(ruta).with(admin()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/movimientos").with(admin()).with(csrf())
                        .param("productoId", "1").param("tipo", "SALIDA").param("cantidad", "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/productos").with(admin()).with(csrf())
                        .param("codigoBarras", "1").param("nombre", "X").param("stockMinimo", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminConLoginRealTampocoVeInventariosPeroSiSuPanel() throws Exception {
        MockHttpSession sesion = iniciarSesion("admin", "admin123");
        mockMvc.perform(get("/").session(sesion)).andExpect(redirectedUrl("/admin"));
        mockMvc.perform(get("/reporte.csv").session(sesion)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").session(sesion)).andExpect(status().isOk());
    }

    @Test
    void adminQueAbreLaRaizVaASuPanelSinVerElInventario() throws Exception {
        mockMvc.perform(get("/").with(admin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
        mockMvc.perform(get("/").param("codigo", "7701").with(admin()))
                .andExpect(redirectedUrl("/admin"));
        // Un POST a la raíz no es "abrir la página": sigue siendo 403.
        mockMvc.perform(post("/").with(admin()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginaDeAccesoDenegadoDelAdminLeExplicaQueNoVeInventarios() throws Exception {
        mockMvc.perform(get("/productos").with(admin())).andExpect(status().isForbidden());
        mockMvc.perform(paginaDeError403().with(admin()))
                .andExpect(content().string(allOf(
                        containsString("El administrador de la plataforma no tiene acceso a los inventarios de las empresas."),
                        containsString("Ir a empresas"),
                        not(containsString("comunícate con el")),
                        not(containsString("Volver a Escanear")))));
    }

    @Test
    void paginaDeAccesoDenegadoDeLaEmpresaLaDevuelveAEscanear() throws Exception {
        Datos datos = registrar("Tienda Sin Panel");
        RequestPostProcessor empresa = user(new UsuarioAutenticado(
                usuarioRepository.findByUsername(datos.username()).orElseThrow()));

        mockMvc.perform(get("/admin").with(empresa)).andExpect(status().isForbidden());
        mockMvc.perform(paginaDeError403().with(empresa))
                .andExpect(content().string(allOf(
                        containsString("Esta sección es solo para el administrador de la plataforma."),
                        containsString("Volver a Escanear"),
                        not(containsString("Ir a empresas")))));
    }

    @Test
    void encabezadoFechaDeAltaTieneVersionCortaParaPantallasPequenas() throws Exception {
        registrar("Tienda Encabezado");
        mockMvc.perform(get("/admin").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<span class=\"d-none d-lg-inline\">Fecha de alta</span>"),
                        containsString("<abbr class=\"d-lg-none\" title=\"Fecha de alta\">Alta</abbr>"))));
    }

    // --- Utilidades ------------------------------------------------------------------

    private record Datos(Empresa empresa, String username) {
    }

    private Datos registrar(String nombre) {
        int n = SECUENCIA.incrementAndGet();
        String username = "admtest" + n;
        Empresa empresa = empresaService.registrarEmpresa(nombre, "600" + n + "-" + (n % 10), username, CLAVE);
        return new Datos(empresa, username);
    }

    private MockHttpSession iniciarSesion(String username) throws Exception {
        return iniciarSesion(username, CLAVE);
    }

    /** Login real por formulario: la sesión queda registrada en el SessionRegistry. */
    private MockHttpSession iniciarSesion(String username, String clave) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin("/login").user(username).password(clave))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /**
     * MockMvc no hace el despacho de error del contenedor tras un 403, así que la página
     * (error/403.html) se pide directamente a /error con el código de estado, como lo haría Tomcat.
     */
    private static MockHttpServletRequestBuilder paginaDeError403() {
        return get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                .accept(MediaType.TEXT_HTML);
    }

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(new Usuario("admin", "x", Rol.ADMIN_PLATAFORMA, null)));
    }
}
