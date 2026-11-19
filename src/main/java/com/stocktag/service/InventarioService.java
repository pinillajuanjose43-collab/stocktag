package com.stocktag.service;

import com.stocktag.model.Empresa;
import com.stocktag.model.Movimiento;
import com.stocktag.model.Producto;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.MovimientoRepository;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio del inventario.
 *
 * <p>REGLA DE ORO: la empresa se obtiene siempre del {@link UsuarioAutenticado} que está en
 * el contexto de seguridad. Ningún método recibe la empresa por parámetro, y toda consulta
 * de productos o movimientos se filtra por ella.
 */
@Service
@Transactional
public class InventarioService {

    /** Encabezados del reporte CSV, en español. */
    static final String ENCABEZADOS_CSV = "Código de barras;Nombre;Cantidad;Stock mínimo;Estado";

    /** Movimientos por página en el historial. */
    public static final int MOVIMIENTOS_POR_PAGINA = 25;

    /** BOM de UTF-8: hace que Excel reconozca las tildes al abrir el archivo. */
    private static final String BOM_UTF8 = "\uFEFF";

    private final ProductoRepository productoRepository;
    private final MovimientoRepository movimientoRepository;
    private final EmpresaRepository empresaRepository;

    public InventarioService(ProductoRepository productoRepository,
                             MovimientoRepository movimientoRepository,
                             EmpresaRepository empresaRepository) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
        this.empresaRepository = empresaRepository;
    }

    /**
     * Registra una ENTRADA (suma) o una SALIDA (resta) sobre un producto de la empresa
     * autenticada. El movimiento queda guardado con la fecha y hora actuales.
     *
     * @throws StockInsuficienteException si una SALIDA deja el stock en negativo
     * @throws ProductoNoEncontradoException si el producto no es de la empresa autenticada
     */
    public Movimiento registrarMovimiento(Long productoId, TipoMovimiento tipo, int cantidad) {
        if (tipo == null) {
            throw new InventarioException("Debe indicar si el movimiento es una entrada o una salida.");
        }
        if (cantidad <= 0) {
            throw new InventarioException("La cantidad del movimiento debe ser mayor que cero.");
        }
        Producto producto = buscarProductoDeLaEmpresa(productoId);

        int nuevaCantidad;
        if (tipo == TipoMovimiento.ENTRADA) {
            if (cantidad > Integer.MAX_VALUE - producto.getCantidad()) {
                throw new InventarioException("La cantidad supera el máximo permitido para un producto.");
            }
            nuevaCantidad = producto.getCantidad() + cantidad;
        } else {
            if (cantidad > producto.getCantidad()) {
                throw new StockInsuficienteException(producto.getNombre(), producto.getCantidad(), cantidad);
            }
            nuevaCantidad = producto.getCantidad() - cantidad;
        }
        producto.setCantidad(nuevaCantidad);
        return movimientoRepository.save(new Movimiento(producto, tipo, cantidad));
    }

    /** Productos de la empresa autenticada, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<Producto> consultarStock() {
        return productoRepository.findByEmpresaIdOrderByNombreAsc(empresaActualId());
    }

    /** Productos de la empresa autenticada con {@code cantidad <= stockMinimo}. */
    @Transactional(readOnly = true)
    public List<Producto> verificarAlertasStock() {
        return productoRepository.buscarConStockBajo(empresaActualId());
    }

    /**
     * Alertas de stock bajo de la empresa autenticada (a partir de {@link #verificarAlertasStock()}),
     * con las unidades que faltan para llegar al mínimo. Primero los agotados y luego el resto,
     * cada grupo ordenado por nombre.
     */
    @Transactional(readOnly = true)
    public List<AlertaStock> listarAlertasStock() {
        return verificarAlertasStock().stream()
                .map(AlertaStock::de)
                .sorted(Comparator.comparing((AlertaStock a) -> !a.agotado()))
                .toList();
    }

    /**
     * Si la empresa autenticada tiene al menos un producto. Las vistas lo usan para distinguir
     * "no hay alertas" de "todavía no hay productos" (estado vacío con el botón para crear el primero).
     */
    @Transactional(readOnly = true)
    public boolean tieneProductos() {
        return productoRepository.existsByEmpresaId(empresaActualId());
    }

    /** Número de productos de la empresa autenticada con stock bajo (contador de la barra de navegación). */
    @Transactional(readOnly = true)
    public long contarAlertasStock() {
        return productoRepository.contarConStockBajo(empresaActualId());
    }

    /**
     * Historial de movimientos de la empresa autenticada, del más reciente al más antiguo,
     * en páginas de {@value #MOVIMIENTOS_POR_PAGINA}. Todos los filtros son opcionales (null = sin filtro);
     * {@code desde} y {@code hasta} incluyen el día completo.
     *
     * @param pagina número de página empezando en 1. Si se sale del rango se ajusta a la primera o la última.
     * @throws InventarioException si {@code desde} es posterior a {@code hasta}
     */
    @Transactional(readOnly = true)
    public Page<Movimiento> consultarMovimientos(TipoMovimiento tipo, LocalDate desde, LocalDate hasta, int pagina) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new InventarioException("La fecha \"desde\" no puede ser posterior a la fecha \"hasta\".");
        }
        Long empresaId = empresaActualId();
        LocalDateTime inicio = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime fin = hasta != null ? hasta.atTime(LocalTime.MAX) : null;

        int indice = Math.max(pagina, 1) - 1;
        Page<Movimiento> resultado = movimientoRepository.buscarHistorial(
                empresaId, tipo, inicio, fin, PageRequest.of(indice, MOVIMIENTOS_POR_PAGINA));
        if (indice > 0 && indice >= resultado.getTotalPages() && resultado.getTotalPages() > 0) {
            resultado = movimientoRepository.buscarHistorial(
                    empresaId, tipo, inicio, fin, PageRequest.of(resultado.getTotalPages() - 1, MOVIMIENTOS_POR_PAGINA));
        }
        return resultado;
    }

    /** Nombre del archivo de descarga del reporte: {@code stocktag-inventario-AAAA-MM-DD.csv} (fecha de hoy). */
    public String nombreArchivoReporte() {
        return "stocktag-inventario-" + LocalDate.now() + ".csv";
    }

    /**
     * Reporte de stock de la empresa autenticada en CSV (UTF-8, separador ';', encabezados en español).
     * Devuelve los bytes listos para enviar como descarga.
     */
    @Transactional(readOnly = true)
    public byte[] generarReporteCSV() {
        StringBuilder csv = new StringBuilder(BOM_UTF8).append(ENCABEZADOS_CSV).append("\r\n");
        for (Producto p : consultarStock()) {
            csv.append(celdaCsv(p.getCodigoBarras())).append(';')
                    .append(celdaCsv(p.getNombre())).append(';')
                    .append(p.getCantidad()).append(';')
                    .append(p.getStockMinimo()).append(';')
                    .append(p.tieneStockBajo() ? "Stock bajo" : "OK")
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Busca por código de barras dentro de la empresa autenticada (flujo de escaneo). */
    @Transactional(readOnly = true)
    public Optional<Producto> buscarPorCodigoBarras(String codigoBarras) {
        if (codigoBarras == null || codigoBarras.isBlank()) {
            return Optional.empty();
        }
        return productoRepository.findByEmpresaIdAndCodigoBarras(empresaActualId(), codigoBarras.trim());
    }

    /**
     * Crea un producto en la empresa autenticada con cantidad 0. El stock inicial se carga
     * con una ENTRADA para que quede en el historial de movimientos.
     */
    public Producto crearProducto(String codigoBarras, String nombre, int stockMinimo) {
        if (codigoBarras == null || codigoBarras.isBlank()) {
            throw new InventarioException("El código de barras es obligatorio.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new InventarioException("El nombre del producto es obligatorio.");
        }
        if (stockMinimo < 0) {
            throw new InventarioException("El stock mínimo no puede ser negativo.");
        }
        Long empresaId = empresaActualId();
        String codigo = codigoBarras.trim();
        if (productoRepository.existsByEmpresaIdAndCodigoBarras(empresaId, codigo)) {
            throw new InventarioException("Ya existe un producto con el código de barras " + codigo + ".");
        }
        Empresa empresa = empresaRepository.getReferenceById(empresaId);
        return productoRepository.save(new Producto(codigo, nombre.trim(), stockMinimo, empresa));
    }

    /**
     * Crea el producto y, si {@code stockInicial} es mayor que cero, registra esa cantidad
     * como una ENTRADA. Todo ocurre en la misma transacción: si algo falla, no se crea nada.
     */
    public Producto crearProducto(String codigoBarras, String nombre, int stockMinimo, Integer stockInicial) {
        if (stockInicial != null && stockInicial < 0) {
            throw new InventarioException("El stock inicial no puede ser negativo.");
        }
        Producto producto = crearProducto(codigoBarras, nombre, stockMinimo);
        if (stockInicial != null && stockInicial > 0) {
            registrarMovimiento(producto.getId(), TipoMovimiento.ENTRADA, stockInicial);
        }
        return producto;
    }

    private Producto buscarProductoDeLaEmpresa(Long productoId) {
        if (productoId == null) {
            throw new ProductoNoEncontradoException();
        }
        return productoRepository.findByIdAndEmpresaId(productoId, empresaActualId())
                .orElseThrow(ProductoNoEncontradoException::new);
    }

    /** Id de la empresa del usuario autenticado. Única fuente de la empresa en este service. */
    private Long empresaActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.getEmpresaId() != null) {
            return usuario.getEmpresaId();
        }
        throw new AccessDeniedException("Solo un usuario de empresa puede acceder al inventario.");
    }

    /**
     * Escapa una celda: va entre comillas si contiene ; , comillas o saltos de línea
     * (las comillas internas se duplican). También neutraliza fórmulas (=, +, -, @)
     * para que una hoja de cálculo no las ejecute al abrir el archivo.
     */
    private static String celdaCsv(String valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor;
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(";") || texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
