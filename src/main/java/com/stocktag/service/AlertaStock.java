package com.stocktag.service;

import com.stocktag.model.Producto;

/**
 * Producto con alerta de stock bajo ({@code cantidad <= stockMinimo}) y cuántas unidades
 * le faltan para llegar a su stock mínimo. Si la cantidad es igual al mínimo, faltan 0.
 */
public record AlertaStock(Producto producto, int unidadesFaltantes) {

    public static AlertaStock de(Producto producto) {
        return new AlertaStock(producto, Math.max(0, producto.getStockMinimo() - producto.getCantidad()));
    }

    /** Sin unidades: se muestra como "Agotado", aparte del "Stock bajo". */
    public boolean agotado() {
        return producto.getCantidad() == 0;
    }
}
