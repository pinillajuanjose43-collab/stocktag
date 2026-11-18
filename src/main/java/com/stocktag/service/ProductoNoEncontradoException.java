package com.stocktag.service;

/** El producto no existe o pertenece a otra empresa (no se distingue a propósito). */
public class ProductoNoEncontradoException extends InventarioException {

    public ProductoNoEncontradoException() {
        super("El producto no existe en el inventario de su empresa.");
    }
}
