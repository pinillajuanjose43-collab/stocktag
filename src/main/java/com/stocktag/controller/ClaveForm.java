package com.stocktag.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Formulario "Restablecer contraseña" del panel /admin. Mismas reglas que la contraseña de
 * {@link EmpresaForm}; el límite de 72 bytes (tildes incluidas) lo revisa EmpresaService.
 */
@ClavesCoinciden
public class ClaveForm implements FormularioConClave {

    @NotNull(message = "Escribe la contraseña nueva.")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    @Pattern(regexp = "(?s)|.*\\S.*", message = "La contraseña no puede ser solo espacios.")
    private String password;

    private String confirmacion;

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getConfirmacion() {
        return confirmacion;
    }

    public void setConfirmacion(String confirmacion) {
        this.confirmacion = confirmacion;
    }
}
