package com.stocktag.controller;

/**
 * Formulario con contraseña y confirmación ("Nueva empresa" y "Restablecer contraseña"),
 * para que ambos compartan la validación {@link ClavesCoinciden}.
 */
public interface FormularioConClave {

    String getPassword();

    String getConfirmacion();
}
