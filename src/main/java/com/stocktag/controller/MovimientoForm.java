package com.stocktag.controller;

import com.stocktag.model.TipoMovimiento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Datos del formulario de ENTRADA / SALIDA del dashboard. El producto se busca siempre
 * dentro de la empresa autenticada, así que un id ajeno simplemente no se encuentra.
 */
public class MovimientoForm {

    @NotNull(message = "Escanea un producto antes de registrar el movimiento.")
    private Long productoId;

    /** Solo se usa para volver a mostrar el producto después de la acción. */
    private String codigoBarras;

    @NotNull(message = "Elige si es una entrada o una salida.")
    private TipoMovimiento tipo;

    @NotNull(message = "Indica la cantidad.")
    @Min(value = 1, message = "La cantidad debe ser mayor que cero.")
    @Max(value = 1_000_000, message = "La cantidad no puede superar 1.000.000.")
    private Integer cantidad;

    public Long getProductoId() {
        return productoId;
    }

    public void setProductoId(Long productoId) {
        this.productoId = productoId;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimiento tipo) {
        this.tipo = tipo;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
