package com.stocktag.service;

import com.stocktag.model.Empresa;
import com.stocktag.model.Movimiento;
import com.stocktag.model.Producto;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.repository.EmpresaRepository;
import com.stocktag.repository.MovimientoRepository;
import com.stocktag.repository.ProductoRepository;
import com.stocktag.security.UsuarioAutenticado;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    static final String ENCABEZADOS_CSV = "Código de barras,Nombre,Cantidad,Stock mínimo,Estado";

    /** BOM de UTF-8: hace que Excel reconozca las tildes al abrir el archivo. */
    private static final String BOM_UTF8 = "﻿";

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
     * Reporte de stock de la empresa autenticada en CSV (UTF-8, encabezados en español).
     * Devuelve los bytes listos para enviar como descarga.
     */
    @Transactional(readOnly = true)
    public byte[] generarReporteCSV() {
        StringBuilder csv = new StringBuilder(BOM_UTF8).append(ENCABEZADOS_CSV).append("\r\n");
        for (Producto p : consultarStock()) {
            csv.append(celdaCsv(p.getCodigoBarras())).append(',')
                    .append(celdaCsv(p.getNombre())).append(',')
                    .append(p.getCantidad()).append(',')
                    .append(p.getStockMinimo()).append(',')
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
     * Escapa una celda según RFC 4180 y neutraliza fórmulas (=, +, -, @) para que
     * una hoja de cálculo no las ejecute al abrir el archivo.
     */
    private static String celdaCsv(String valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor;
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
