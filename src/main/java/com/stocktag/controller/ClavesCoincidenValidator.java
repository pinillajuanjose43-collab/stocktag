package com.stocktag.controller;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ClavesCoincidenValidator implements ConstraintValidator<ClavesCoinciden, EmpresaForm> {

    @Override
    public boolean isValid(EmpresaForm form, ConstraintValidatorContext contexto) {
        String clave = form.getPassword();
        String confirmacion = form.getConfirmacion();
        if (clave == null || clave.isEmpty()) {
            return true; // El campo contraseña ya muestra su propio error.
        }
        if (clave.equals(confirmacion)) {
            return true;
        }
        String mensaje = (confirmacion == null || confirmacion.isEmpty())
                ? "Repite la contraseña para confirmarla."
                : "Las contraseñas no coinciden.";
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(mensaje)
                .addPropertyNode("confirmacion")
                .addConstraintViolation();
        return false;
    }
}
