package com.stocktag.service;

/**
 * Error de una regla de negocio del inventario. El mensaje está en español
 * y está pensado para mostrarse tal cual en la vista.
 */
public class InventarioException extends RuntimeException {

    public InventarioException(String mensaje) {
        super(mensaje);
    }
}
