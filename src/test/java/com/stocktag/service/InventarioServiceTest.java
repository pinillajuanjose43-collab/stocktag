package com.stocktag.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de InventarioService con repositorios reales sobre H2 en memoria
 * ({@code @DataJpaTest} no toca ./data). La sesión se simula poniendo un
 * UsuarioAutenticado en el contexto de seguridad, igual que tras el login.
 */
@DataJpaTest
@Import(InventarioService.class)
class InventarioServiceTest {

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Empresa ferreteria;
    private Empresa papeleria;

    @BeforeEach
    void crearEmpresas() {
        ferreteria = empresaRepository.save(new Empresa("Ferretería El Tornillo", "900111222-1"));
        papeleria = empresaRepository.save(new Empresa("Papelería La Hoja", "900333444-2"));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // --- Movimientos ---------------------------------------------------------------

    @Test
    void salidaQueDejaStockNegativoLanzaExcepcionYNoCambiaNada() {
        iniciarSesionComo(ferreteria);
        Producto martillo = crearConStock("7701", "Martillo", 2, 5);

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(martillo.getId(), TipoMovimiento.SALIDA, 6))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessage("Stock insuficiente para \"Martillo\": hay 5 unidades y se intentó sacar 6.");

        sincronizar();
        assertThat(productoRepository.findById(martillo.getId()).orElseThrow().getCantidad()).isEqualTo(5);
        // Solo existe la ENTRADA inicial; la salida rechazada no se guardó.
        assertThat(movimientoRepository.findByProductoEmpresaIdOrderByFechaDesc(ferreteria.getId()))
                .extracting(Movimiento::getTipo)
                .containsExactly(TipoMovimiento.ENTRADA);
    }

    @Test
    void salidaPuedeDejarElStockExactamenteEnCero() {
        iniciarSesionComo(ferreteria);
        Producto martillo = crearConStock("7701", "Martillo", 2, 5);

        inventarioService.registrarMovimiento(martillo.getId(), TipoMovimiento.SALIDA, 5);

        sincronizar();
        assertThat(productoRepository.findById(martillo.getId()).orElseThrow().getCantidad()).isZero();
    }

    @Test
    void entradaSumaAlStockYGuardaFechaYHora() {
        iniciarSesionComo(ferreteria);
        Producto tornillos = inventarioService.crearProducto("7702", "Caja de tornillos", 10);
        LocalDateTime antes = LocalDateTime.now();

        inventarioService.registrarMovimiento(tornillos.getId(), TipoMovimiento.ENTRADA, 30);
        Movimiento segunda = inventarioService.registrarMovimiento(tornillos.getId(), TipoMovimiento.ENTRADA, 12);

        sincronizar();
        assertThat(productoRepository.findById(tornillos.getId()).orElseThrow().getCantidad()).isEqualTo(42);
        Movimiento guardado = movimientoRepository.findById(segunda.getId()).orElseThrow();
        assertThat(guardado.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(guardado.getCantidad()).isEqualTo(12);
        assertThat(guardado.getFecha()).isNotNull().isAfterOrEqualTo(antes.withNano(0));
    }

    @Test
    void cantidadDelMovimientoDebeSerPositiva() {
        iniciarSesionComo(ferreteria);
        Producto martillo = crearConStock("7701", "Martillo", 2, 5);

        assertThatThrownBy(() -> inventarioService.registrarMovimiento(martillo.getId(), TipoMovimiento.ENTRADA, 0))
                .isInstanceOf(InventarioException.class)
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> inventarioService.registrarMovimiento(martillo.getId(), TipoMovimiento.SALIDA, -3))
                .isInstanceOf(InventarioException.class);
    }

    // --- Alertas ---------------------------------------------------------------------

    @Test
    void alertaCuandoCantidadEsMenorOIgualAlStockMinimo() {
        iniciarSesionComo(ferreteria);
        crearConStock("1", "Brocha", 5, 2);    // por debajo  -> alerta
        crearConStock("2", "Lija", 5, 5);      // igual       -> alerta
        crearConStock("3", "Pegante", 5, 6);   // por encima  -> sin alerta
        crearConStock("4", "Cinta", 0, 0);     // 0 <= 0      -> alerta

        List<Producto> alertas = inventarioService.verificarAlertasStock();

        assertThat(alertas).extracting(Producto::getNombre).containsExactly("Brocha", "Cinta", "Lija");
    }

    @Test
    void alertaDesapareceAlReponerStock() {
        iniciarSesionComo(ferreteria);
        Producto lija = crearConStock("2", "Lija", 5, 5);
        assertThat(inventarioService.verificarAlertasStock()).extracting(Producto::getId).containsExactly(lija.getId());

        inventarioService.registrarMovimiento(lija.getId(), TipoMovimiento.ENTRADA, 1);

        assertThat(inventarioService.verificarAlertasStock()).isEmpty();
    }

    // --- Aislamiento entre empresas --------------------------------------------------

    @Test
    void unaEmpresaNoVeLosProductosDeOtra() {
        iniciarSesionComo(ferreteria);
        crearConStock("7701", "Martillo", 10, 3);   // la ferretería tiene una alerta

        iniciarSesionComo(papeleria);
        crearConStock("5501", "Cuaderno", 2, 8);

        assertThat(inventarioService.consultarStock()).extracting(Producto::getNombre).containsExactly("Cuaderno");
        assertThat(inventarioService.verificarAlertasStock()).isEmpty();
        assertThat(inventarioService.buscarPorCodigoBarras("7701")).isEmpty();
        assertThat(new String(inventarioService.generarReporteCSV(), StandardCharsets.UTF_8))
                .contains("Cuaderno")
                .doesNotContain("Martillo");
    }

    @Test
    void unaEmpresaNoPuedeMoverStockDeOtraConociendoElId() {
        iniciarSesionComo(ferreteria);
        Producto martillo = crearConStock("7701", "Martillo", 2, 5);

        iniciarSesionComo(papeleria);
        assertThatThrownBy(() -> inventarioService.registrarMovimiento(martillo.getId(), TipoMovimiento.SALIDA, 1))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessage("El producto no existe en el inventario de su empresa.");

        sincronizar();
        assertThat(productoRepository.findById(martillo.getId()).orElseThrow().getCantidad()).isEqualTo(5);
    }

    @Test
    void elMismoCodigoPuedeExistirEnDosEmpresasPeroNoRepetirseEnUna() {
        iniciarSesionComo(ferreteria);
        inventarioService.crearProducto("7701", "Martillo", 1);
        assertThatThrownBy(() -> inventarioService.crearProducto(" 7701 ", "Otro martillo", 1))
                .isInstanceOf(InventarioException.class)
                .hasMessage("Ya existe un producto con el código de barras 7701.");

        iniciarSesionComo(papeleria);
        Producto deLaPapeleria = inventarioService.crearProducto("7701", "Borrador", 1);
        assertThat(deLaPapeleria.getId()).isNotNull();
    }

    @Test
    void laBaseDeDatosImpideCodigoRepetidoEnLaMismaEmpresa() {
        productoRepository.saveAndFlush(new Producto("9999", "Uno", 0, ferreteria));
        productoRepository.saveAndFlush(new Producto("9999", "Mismo código, otra empresa", 0, papeleria));

        assertThatThrownBy(() -> productoRepository.saveAndFlush(new Producto("9999", "Dos", 0, ferreteria)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sinUsuarioDeEmpresaNoHayAccesoAlInventario() {
        assertThatThrownBy(() -> inventarioService.consultarStock()).isInstanceOf(AccessDeniedException.class);

        UsuarioAutenticado admin = new UsuarioAutenticado(new Usuario("admin", "x", Rol.ADMIN_PLATAFORMA, null));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        assertThatThrownBy(() -> inventarioService.consultarStock()).isInstanceOf(AccessDeniedException.class);
    }

    // --- CSV -------------------------------------------------------------------------

    @Test
    void reporteCsvEnUtf8ConEncabezadosEnEspanol() {
        iniciarSesionComo(ferreteria);
        crearConStock("7701", "Martillo", 2, 5);
        crearConStock("7703", "Tubería PVC, 1/2\" \"Pavco\"", 4, 1);
        crearConStock("7704", "=HYPERLINK(\"http://x\")", 0, 3);
        crearConStock("7705", "Clavos; 2 pulgadas", 1, 10);

        byte[] bytes = inventarioService.generarReporteCSV();
        String csv = new String(bytes, StandardCharsets.UTF_8);

        // BOM de UTF-8 al inicio, para que Excel muestre bien las tildes
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String[] lineas = csv.substring(1).split("\r\n");
        assertThat(lineas[0]).isEqualTo("Código de barras;Nombre;Cantidad;Stock mínimo;Estado");
        assertThat(lineas).hasSize(5);
        assertThat(lineas[1]).isEqualTo("7704;\"'=HYPERLINK(\"\"http://x\"\")\";3;0;OK");
        assertThat(lineas[2]).isEqualTo("7705;\"Clavos; 2 pulgadas\";10;1;OK");
        assertThat(lineas[3]).isEqualTo("7701;Martillo;5;2;OK");
        assertThat(lineas[4]).isEqualTo("7703;\"Tubería PVC, 1/2\"\" \"\"Pavco\"\"\";1;4;Stock bajo");
    }

    // --- Utilidades ------------------------------------------------------------------

    private void iniciarSesionComo(Empresa empresa) {
        Usuario usuario = new Usuario("usuario-" + empresa.getId(), "x", Rol.EMPRESA, empresa);
        UsuarioAutenticado principal = new UsuarioAutenticado(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Crea el producto en la empresa con sesión iniciada y carga el stock con una ENTRADA. */
    private Producto crearConStock(String codigo, String nombre, int stockMinimo, int cantidad) {
        Producto producto = inventarioService.crearProducto(codigo, nombre, stockMinimo);
        if (cantidad > 0) {
            inventarioService.registrarMovimiento(producto.getId(), TipoMovimiento.ENTRADA, cantidad);
        }
        return producto;
    }

    /** Escribe en la base y vacía la caché de JPA para leer lo que realmente quedó guardado. */
    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }
}
