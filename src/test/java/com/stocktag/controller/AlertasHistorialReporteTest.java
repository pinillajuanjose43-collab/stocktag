package com.stocktag.controller;

import com.stocktag.model.Empresa;
import com.stocktag.model.Movimiento;
import com.stocktag.model.Producto;
import com.stocktag.model.Rol;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.MovimientoRepository;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.security.UsuarioAutenticado;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pruebas de las vistas de alertas (/alertas), historial de movimientos (GET /movimientos)
 * y descarga del CSV (/reporte.csv). Cada prueba corre en una transacción que se revierte;
 * H2 en memoria, no toca ./data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocktag-test;DB_CLOSE_DELAY=-1",
        "stocktag.admin.username=admin",
        "stocktag.admin.password=admin123"
})
@AutoConfigureMockMvc
@Transactional
class AlertasHistorialReporteTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private EntityManager entityManager;

    private Empresa ferreteria;
    private Empresa papeleria;

    @BeforeEach
    void datos() {
        ferreteria = nuevaEmpresa("Ferretería Prueba");
        papeleria = nuevaEmpresa("Papelería Ajena");
    }

    // --- Alertas -----------------------------------------------------------------------

    @Test
    void alertasMuestraLasUnidadesQueFaltanYElAgotadoAparte() throws Exception {
        producto(ferreteria, "7701", "Martillo", 5, 2);   // faltan 3
        producto(ferreteria, "7702", "Brocha", 4, 0);     // agotado, faltan 4
        producto(ferreteria, "7703", "Pegante", 1, 9);    // sin alerta

        mockMvc.perform(get("/alertas").with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/alertas"))
                .andExpect(content().string(allOf(
                        containsString("Martillo"),
                        containsString("Brocha"),
                        not(containsString("Pegante")),
                        containsString("Faltan para el mínimo"),
                        containsString("faltan\">3<"),
                        containsString("faltan\">4<"),
                        containsString("fila-agotado"),
                        containsString("Agotado"),
                        containsString("fila-stock-bajo"),
                        containsString("Stock bajo"),
                        containsString("/reporte.csv"),
                        containsString("Descargar CSV"))));
    }

    @Test
    void alertasDeUnaEmpresaNoMuestranProductosDeOtra() throws Exception {
        producto(ferreteria, "7701", "Martillo", 5, 2);
        producto(papeleria, "5501", "Cuaderno", 10, 1);

        mockMvc.perform(get("/alertas").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString("Martillo"),
                        not(containsString("Cuaderno")))));
    }

    @Test
    void alertasSinProductosEnAlertaMuestraEstadoVacio() throws Exception {
        producto(ferreteria, "7701", "Martillo", 1, 9);
        producto(papeleria, "5501", "Cuaderno", 10, 1);   // alerta de otra empresa: no cuenta

        mockMvc.perform(get("/alertas").with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("No hay alertas de stock"),
                        not(containsString("<table")),
                        not(containsString("Cuaderno")))));
    }

    @Test
    void barraDeNavegacionMuestraElContadorDeAlertasComoTexto() throws Exception {
        producto(ferreteria, "7701", "Martillo", 5, 2);
        producto(ferreteria, "7702", "Brocha", 4, 0);
        producto(papeleria, "5501", "Cuaderno", 10, 1);   // de otra empresa: no suma

        for (String ruta : new String[]{"/", "/productos", "/alertas", "/movimientos"}) {
            mockMvc.perform(get(ruta).with(sesion(ferreteria)))
                    .andExpect(content().string(allOf(
                            containsString("aria-label=\"Alertas: 2 productos con stock bajo\""),
                            containsString("contador-alertas cifra\">2<"),
                            containsString("href=\"/movimientos\""))));
        }

        mockMvc.perform(get("/").with(sesion(papeleria)))
                .andExpect(content().string(containsString("Alertas: 1 producto con stock bajo")));
    }

    @Test
    void sinAlertasLaBarraNoMuestraContador() throws Exception {
        producto(ferreteria, "7701", "Martillo", 1, 9);

        mockMvc.perform(get("/productos").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString("Alertas: ningún producto con stock bajo"),
                        not(containsString("contador-alertas")))));
    }

    @Test
    void listaDeProductosTieneElBotonDescargarCsv() throws Exception {
        producto(ferreteria, "7701", "Martillo", 1, 9);

        mockMvc.perform(get("/productos").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString("href=\"/reporte.csv\""),
                        containsString("Descargar CSV"))));
    }

    // --- Historial de movimientos ------------------------------------------------------

    @Test
    void historialMuestraSoloLosMovimientosDeLaEmpresa() throws Exception {
        Producto martillo = producto(ferreteria, "7701", "Martillo", 1, 10);
        Producto cuaderno = producto(papeleria, "5501", "Cuaderno", 1, 10);
        movimiento(martillo, TipoMovimiento.ENTRADA, 7, LocalDateTime.of(2026, 11, 5, 14, 30));
        movimiento(cuaderno, TipoMovimiento.SALIDA, 3, LocalDateTime.of(2026, 11, 6, 8, 0));

        mockMvc.perform(get("/movimientos").with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/movimientos"))
                .andExpect(content().string(allOf(
                        containsString("Martillo"),
                        containsString("7701"),
                        containsString("05/11/2026"),
                        containsString("14:30"),
                        containsString("datetime=\"2026-11-05T14:30:00\""),
                        containsString("Entrada"),
                        containsString("+7"),
                        not(containsString("Cuaderno")),
                        not(containsString("5501")))));
    }

    @Test
    void historialFiltraPorTipo() throws Exception {
        Producto martillo = producto(ferreteria, "7701", "Martillo", 1, 10);
        Producto brocha = producto(ferreteria, "7702", "Brocha", 1, 10);
        movimiento(martillo, TipoMovimiento.ENTRADA, 7, LocalDateTime.of(2026, 11, 5, 9, 0));
        movimiento(brocha, TipoMovimiento.SALIDA, 2, LocalDateTime.of(2026, 11, 6, 9, 0));

        mockMvc.perform(get("/movimientos").param("tipo", "SALIDA").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString(">Brocha<"),
                        containsString("−2"),
                        not(containsString(">Martillo<")),
                        containsString("<option value=\"SALIDA\" selected=\"selected\">"),
                        containsString("Quitar filtros"))));
    }

    @Test
    void historialFiltraPorRangoDeFechas() throws Exception {
        Producto martillo = producto(ferreteria, "7701", "Martillo", 1, 10);
        Producto brocha = producto(ferreteria, "7702", "Brocha", 1, 10);
        Producto lija = producto(ferreteria, "7703", "Lija", 1, 10);
        movimiento(martillo, TipoMovimiento.ENTRADA, 1, LocalDateTime.of(2026, 10, 31, 23, 59));
        movimiento(brocha, TipoMovimiento.ENTRADA, 1, LocalDateTime.of(2026, 11, 3, 12, 0));
        movimiento(lija, TipoMovimiento.ENTRADA, 1, LocalDateTime.of(2026, 11, 8, 0, 1));

        mockMvc.perform(get("/movimientos").param("desde", "2026-11-01").param("hasta", "2026-11-07")
                        .with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString(">Brocha<"),
                        not(containsString(">Martillo<")),
                        not(containsString(">Lija<")),
                        containsString("value=\"2026-11-01\""),
                        containsString("value=\"2026-11-07\""))));
    }

    @Test
    void filtrosSinResultadosMuestranEstadoVacioDeFiltros() throws Exception {
        Producto martillo = producto(ferreteria, "7701", "Martillo", 1, 10);
        movimiento(martillo, TipoMovimiento.ENTRADA, 1, LocalDateTime.of(2026, 11, 3, 12, 0));

        mockMvc.perform(get("/movimientos").param("tipo", "SALIDA").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString("No hay movimientos con estos filtros."),
                        not(containsString("<table")))));
    }

    @Test
    void rangoDeFechasInvertidoOFechaInvalidaSeMuestranComoAlerta() throws Exception {
        mockMvc.perform(get("/movimientos").param("desde", "2026-11-10").param("hasta", "2026-11-01")
                        .with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("role=\"alert\""),
                        containsString("no puede ser posterior"))));

        mockMvc.perform(get("/movimientos").param("desde", "ayer").with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("role=\"alert\""),
                        containsString("Escribe una fecha válida."))));
    }

    @Test
    void historialSinMovimientosMuestraEstadoVacio() throws Exception {
        mockMvc.perform(get("/movimientos").with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Todavía no hay movimientos"),
                        not(containsString("<table")))));
    }

    @Test
    void historialPaginaDe25En25ConservandoLosFiltros() throws Exception {
        Producto martillo = producto(ferreteria, "7701", "Martillo", 1, 0);
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 8, 0);
        for (int i = 1; i <= 30; i++) {
            movimiento(martillo, TipoMovimiento.ENTRADA, i, base.plusHours(i));
        }

        MvcResult primera = mockMvc.perform(get("/movimientos").param("tipo", "ENTRADA").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString("1–25"),
                        containsString(">30<"),
                        containsString("Página <span class=\"cifra fw-medium text-body\">1</span>"),
                        containsString("href=\"/movimientos?tipo=ENTRADA&amp;pagina=2\""),
                        containsString("+30"),          // la más reciente va primero
                        not(containsString(">+5<")))))   // las 5 más antiguas quedan en la página 2
                .andReturn();
        assertThat(contarFilas(primera)).isEqualTo(25);

        MvcResult segunda = mockMvc.perform(get("/movimientos").param("tipo", "ENTRADA").param("pagina", "2")
                        .with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString("26–30"),
                        containsString("href=\"/movimientos?tipo=ENTRADA\""),   // anterior / primera
                        containsString(">+5<"),
                        containsString(">+1<"))))
                .andReturn();
        assertThat(contarFilas(segunda)).isEqualTo(5);
    }

    @Test
    void elPostDeMovimientosSigueFuncionandoYApareceEnElHistorial() throws Exception {
        Producto martillo = producto(ferreteria, "7701", "Martillo", 1, 5);

        mockMvc.perform(post("/movimientos").with(sesion(ferreteria)).with(csrf())
                        .param("productoId", martillo.getId().toString())
                        .param("codigoBarras", "7701")
                        .param("tipo", "SALIDA")
                        .param("cantidad", "2"))
                .andExpect(redirectedUrl("/?codigo=7701"));

        String hoy = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mockMvc.perform(get("/movimientos").with(sesion(ferreteria)))
                .andExpect(content().string(allOf(
                        containsString(">Martillo<"),
                        containsString("Salida"),
                        containsString("−2"),
                        containsString(hoy))));
    }

    // --- Descarga del CSV --------------------------------------------------------------

    @Test
    void descargaDelCsvConContentTypeYNombreDeArchivo() throws Exception {
        producto(ferreteria, "7701", "Martillo", 5, 2);
        producto(papeleria, "5501", "Cuaderno", 10, 1);
        String nombre = "stocktag-inventario-" + LocalDate.now() + ".csv";

        MvcResult resultado = mockMvc.perform(get("/reporte.csv").with(sesion(ferreteria)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombre + "\""))
                .andReturn();

        assertThat(nombre).matches("stocktag-inventario-\\d{4}-\\d{2}-\\d{2}\\.csv");
        byte[] bytes = resultado.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv)
                .contains("Código de barras;Nombre;Cantidad;Stock mínimo;Estado")
                .contains("7701;Martillo;2;5;Stock bajo")
                .doesNotContain("Cuaderno");
    }

    @Test
    void elAdministradorNoPuedeDescargarElCsvNiVerElHistorial() throws Exception {
        UsuarioAutenticado admin = new UsuarioAutenticado(new Usuario("admin-x", "x", Rol.ADMIN_PLATAFORMA, null));

        mockMvc.perform(get("/reporte.csv").with(user(admin))).andExpect(status().isForbidden());
        mockMvc.perform(get("/movimientos").with(user(admin))).andExpect(status().isForbidden());
        mockMvc.perform(get("/alertas").with(user(admin))).andExpect(status().isForbidden());
    }

    // --- Utilidades --------------------------------------------------------------------

    private Empresa nuevaEmpresa(String nombre) {
        return empresaRepository.save(new Empresa(nombre, "700" + SECUENCIA.incrementAndGet() + "-9"));
    }

    private Producto producto(Empresa duena, String codigo, String nombre, int stockMinimo, int cantidad) {
        Producto producto = new Producto(codigo, nombre, stockMinimo, duena);
        producto.setCantidad(cantidad);
        return productoRepository.save(producto);
    }

    /** Guarda un movimiento con una fecha dada (la entidad no expone un setter de fecha). */
    private void movimiento(Producto producto, TipoMovimiento tipo, int cantidad, LocalDateTime fecha) {
        Movimiento movimiento = movimientoRepository.saveAndFlush(new Movimiento(producto, tipo, cantidad));
        entityManager.createQuery("update Movimiento m set m.fecha = :fecha where m.id = :id")
                .setParameter("fecha", fecha)
                .setParameter("id", movimiento.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private static int contarFilas(MvcResult resultado) throws Exception {
        String html = resultado.getResponse().getContentAsString();
        String cuerpo = html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
        return cuerpo.split("<tr", -1).length - 1;
    }

    private RequestPostProcessor sesion(Empresa duena) {
        UsuarioAutenticado principal = new UsuarioAutenticado(
                new Usuario("usuario-" + duena.getId(), "x", Rol.EMPRESA, duena));
        return user(principal);
    }
}
