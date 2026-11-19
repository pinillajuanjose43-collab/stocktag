package com.stocktag.controller;

import com.stocktag.model.Empresa;
import com.stocktag.model.Producto;
import com.stocktag.model.Rol;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.model.Usuario;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.MovimientoRepository;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

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
 * Pruebas de las vistas de inventario (dashboard de escaneo, lista y formulario de producto).
 * Cada prueba corre en una transacción que se revierte al final; H2 en memoria, no toca ./data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stocktag-test;DB_CLOSE_DELAY=-1",
        "stocktag.admin.username=admin",
        "stocktag.admin.password=admin123"
})
@AutoConfigureMockMvc
@Transactional
class InventarioVistasTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    private Empresa empresa;
    private Producto martillo;

    @BeforeEach
    void datos() {
        empresa = nuevaEmpresa("Ferretería Prueba");
        martillo = producto(empresa, "7701", "Martillo", 2, 5);
    }

    // --- Dashboard y escaneo -----------------------------------------------------------

    @Test
    void dashboardTieneElInputDeEscaneoConAutofocus() throws Exception {
        mockMvc.perform(get("/").with(sesion(empresa)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("name=\"codigo\""),
                        containsString("autofocus"),
                        containsString("Escanea un producto"))));
    }

    @Test
    void escanearCodigoExistenteMuestraElProductoYLosBotones() throws Exception {
        mockMvc.perform(get("/").param("codigo", " 7701 ").with(sesion(empresa)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Martillo"),
                        containsString("value=\"ENTRADA\""),
                        containsString("value=\"SALIDA\""),
                        not(containsString("Código no registrado")))));
    }

    @Test
    void escanearCodigoInexistenteOfreceCrearloConElCodigoLlenado() throws Exception {
        mockMvc.perform(get("/").param("codigo", "999888").with(sesion(empresa)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Código no registrado"),
                        containsString("/productos/nuevo?codigo=999888"))));

        mockMvc.perform(get("/productos/nuevo").param("codigo", "999888").with(sesion(empresa)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"999888\"")));
    }

    @Test
    void elCodigoDeOtraEmpresaNoSeEncuentra() throws Exception {
        Empresa otra = nuevaEmpresa("Papelería Ajena");
        producto(otra, "5501", "Cuaderno", 1, 10);

        mockMvc.perform(get("/").param("codigo", "5501").with(sesion(empresa)))
                .andExpect(content().string(allOf(
                        containsString("Código no registrado"),
                        not(containsString("Cuaderno")))));
    }

    // --- Movimientos -------------------------------------------------------------------

    @Test
    void entradaSumaYVuelveAlDashboardConMensaje() throws Exception {
        mockMvc.perform(movimiento(martillo, "ENTRADA", "3"))
                .andExpect(redirectedUrl("/?codigo=7701"))
                .andExpect(flash().attribute("exito", "Entrada registrada: +3 de Martillo. Stock actual: 8."));

        assertThat(productoRepository.findById(martillo.getId()).orElseThrow().getCantidad()).isEqualTo(8);
    }

    @Test
    void salidaQueDejaStockNegativoSeMuestraComoAlertaNoComoPaginaDeError() throws Exception {
        mockMvc.perform(movimiento(martillo, "SALIDA", "6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?codigo=7701"))
                .andExpect(flash().attribute("error",
                        "Stock insuficiente para \"Martillo\": hay 5 unidades y se intentó sacar 6."));

        assertThat(productoRepository.findById(martillo.getId()).orElseThrow().getCantidad()).isEqualTo(5);

        // Al seguir el redirect, la alerta aparece en el dashboard
        mockMvc.perform(get("/").param("codigo", "7701").flashAttr("error", "Stock insuficiente para Martillo")
                        .with(sesion(empresa)))
                .andExpect(content().string(allOf(
                        containsString("role=\"alert\""),
                        containsString("Stock insuficiente para Martillo"))));
    }

    @Test
    void cantidadInvalidaSeMuestraComoAlerta() throws Exception {
        mockMvc.perform(movimiento(martillo, "SALIDA", "abc"))
                .andExpect(redirectedUrl("/?codigo=7701"))
                .andExpect(flash().attribute("error", "Escribe un número entero."));

        mockMvc.perform(movimiento(martillo, "ENTRADA", "0"))
                .andExpect(flash().attribute("error", "La cantidad debe ser mayor que cero."));
    }

    @Test
    void noSePuedeMoverStockDeOtraEmpresa() throws Exception {
        Empresa otra = nuevaEmpresa("Papelería Ajena");
        Producto ajeno = producto(otra, "5501", "Cuaderno", 1, 10);

        mockMvc.perform(post("/movimientos").with(sesion(empresa)).with(csrf())
                        .param("productoId", ajeno.getId().toString())
                        .param("codigoBarras", "5501")
                        .param("tipo", "SALIDA")
                        .param("cantidad", "1"))
                .andExpect(flash().attribute("error", "El producto no existe en el inventario de su empresa."));

        assertThat(productoRepository.findById(ajeno.getId()).orElseThrow().getCantidad()).isEqualTo(10);
    }

    // --- Lista de productos ------------------------------------------------------------

    @Test
    void listaResaltaLosProductosConStockBajo() throws Exception {
        producto(empresa, "7702", "Lija", 5, 5);
        producto(empresa, "7703", "Brocha", 1, 0);

        mockMvc.perform(get("/productos").with(sesion(empresa)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Martillo"),
                        containsString("fila-stock-bajo"),
                        containsString("Stock bajo"),
                        containsString("fila-agotado"),
                        containsString("Agotado"))));

        mockMvc.perform(get("/productos").param("filtro", "bajo").with(sesion(empresa)))
                .andExpect(content().string(allOf(
                        containsString("Lija"),
                        containsString("Brocha"),
                        not(containsString(">Martillo<")))));
    }

    @Test
    void listaVaciaMuestraEstadoVacioConAcciones() throws Exception {
        Empresa nueva = nuevaEmpresa("Tienda Vacía");

        mockMvc.perform(get("/productos").with(sesion(nueva)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Todavía no tienes productos"),
                        containsString("Crear producto"),
                        not(containsString("<table")))));
    }

    @Test
    void dashboardDeEmpresaSinProductosInvitaACrearElPrimero() throws Exception {
        Empresa nueva = nuevaEmpresa("Tienda Recién Creada");

        mockMvc.perform(get("/").with(sesion(nueva)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Aún no tienes productos"),
                        containsString("Crear primer producto"),
                        containsString("href=\"/productos/nuevo\""),
                        not(containsString("Todos los productos están por encima de su stock mínimo")))));
    }

    @Test
    void dashboardConProductosSinAlertasLoDiceSinEstadoVacio() throws Exception {
        Empresa conStock = nuevaEmpresa("Tienda Surtida");
        producto(conStock, "8801", "Tornillos", 5, 50);

        mockMvc.perform(get("/").with(sesion(conStock)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Todos los productos están por encima de su stock mínimo"),
                        not(containsString("Aún no tienes productos")))));
    }

    // --- Formulario de producto --------------------------------------------------------

    @Test
    void formularioConErroresMuestraLosMensajesEnLosCampos() throws Exception {
        mockMvc.perform(post("/productos").with(sesion(empresa)).with(csrf())
                        .param("codigoBarras", "8801")
                        .param("nombre", " ")
                        .param("stockMinimo", "-1")
                        .param("stockInicial", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/producto-form"))
                .andExpect(content().string(allOf(
                        containsString("Escribe el nombre del producto."),
                        containsString("El stock mínimo no puede ser negativo."),
                        containsString("Escribe un número entero."),
                        containsString("is-invalid"),
                        containsString("aria-describedby=\"nombre-error\""))));

        assertThat(productoRepository.findByEmpresaIdAndCodigoBarras(empresa.getId(), "8801")).isEmpty();
    }

    @Test
    void codigoDuplicadoSeMuestraComoAlertaEnElFormulario() throws Exception {
        mockMvc.perform(post("/productos").with(sesion(empresa)).with(csrf())
                        .param("codigoBarras", "7701")
                        .param("nombre", "Otro martillo")
                        .param("stockMinimo", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("inventario/producto-form"))
                .andExpect(content().string(allOf(
                        containsString("role=\"alert\""),
                        containsString("Ya existe un producto con el código de barras 7701."),
                        containsString("value=\"Otro martillo\""))));
    }

    @Test
    void crearProductoConStockInicialRegistraEntradaYVuelveAlEscaneo() throws Exception {
        mockMvc.perform(post("/productos").with(sesion(empresa)).with(csrf())
                        .param("codigoBarras", "8801")
                        .param("nombre", "Brocha 2 pulgadas")
                        .param("stockMinimo", "3")
                        .param("stockInicial", "12"))
                .andExpect(redirectedUrl("/?codigo=8801"))
                .andExpect(flash().attribute("exito", "Producto creado: Brocha 2 pulgadas. Stock actual: 12."));

        Producto brocha = productoRepository.findByEmpresaIdAndCodigoBarras(empresa.getId(), "8801").orElseThrow();
        assertThat(brocha.getCantidad()).isEqualTo(12);
        assertThat(movimientoRepository.findByProductoIdAndProductoEmpresaIdOrderByFechaDesc(brocha.getId(), empresa.getId()))
                .extracting(m -> m.getTipo())
                .containsExactly(TipoMovimiento.ENTRADA);
    }

    // --- Datos de ejemplo --------------------------------------------------------------

    @Test
    void empresaDeEjemploPuedeIniciarSesion() throws Exception {
        mockMvc.perform(formLogin("/login").user("tienda").password("tienda123"))
                .andExpect(redirectedUrl("/"));
    }

    // --- Utilidades --------------------------------------------------------------------

    private Empresa nuevaEmpresa(String nombre) {
        return empresaRepository.save(new Empresa(nombre, "800" + SECUENCIA.incrementAndGet() + "-9"));
    }

    private Producto producto(Empresa duena, String codigo, String nombre, int stockMinimo, int cantidad) {
        Producto producto = new Producto(codigo, nombre, stockMinimo, duena);
        producto.setCantidad(cantidad);
        return productoRepository.save(producto);
    }

    private RequestPostProcessor sesion(Empresa duena) {
        UsuarioAutenticado principal = new UsuarioAutenticado(
                new Usuario("usuario-" + duena.getId(), "x", Rol.EMPRESA, duena));
        return user(principal);
    }

    private org.springframework.test.web.servlet.RequestBuilder movimiento(Producto producto, String tipo, String cantidad) {
        return post("/movimientos").with(sesion(empresa)).with(csrf())
                .param("productoId", producto.getId().toString())
                .param("codigoBarras", producto.getCodigoBarras())
                .param("tipo", tipo)
                .param("cantidad", cantidad);
    }
}
