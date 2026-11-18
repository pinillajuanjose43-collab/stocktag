package com.stocktag.service;

/**
 * Error de una regla de negocio de la gestión de empresas. El mensaje está en español y se
 * muestra tal cual en la vista. {@code campo} indica el campo del formulario al que se refiere
 * ("nit", "username", ...) para mostrar el error debajo de él; es null si es un error general.
 */
public class EmpresaException extends RuntimeException {

    private final String campo;

    public EmpresaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
