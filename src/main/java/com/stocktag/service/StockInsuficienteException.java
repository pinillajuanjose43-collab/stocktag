package com.stocktag.service;

/** Una SALIDA dejaría el stock del producto en negativo. */
public class StockInsuficienteException extends InventarioException {

    private final int disponible;
    private final int solicitado;

    public StockInsuficienteException(String nombreProducto, int disponible, int solicitado) {
        super("Stock insuficiente para \"" + nombreProducto + "\": hay " + disponible
                + " unidades y se intentó sacar " + solicitado + ".");
        this.disponible = disponible;
        this.solicitado = solicitado;
    }

    public int getDisponible() {
        return disponible;
    }

    public int getSolicitado() {
        return solicitado;
    }
}
