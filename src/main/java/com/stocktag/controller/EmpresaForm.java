package com.stocktag.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos del formulario "Nueva empresa" del panel /admin. Los patrones aceptan el valor vacío
 * para que un campo vacío muestre solo el mensaje de @NotBlank y no dos errores a la vez.
 */
@ClavesCoinciden
public class EmpresaForm implements FormularioConClave {

    @NotBlank(message = "Escribe el nombre de la empresa.")
    @Size(max = 120, message = "El nombre admite máximo 120 caracteres.")
    private String nombre;

    @NotBlank(message = "Escribe el NIT de la empresa.")
    @Pattern(regexp = "\\s*|\\s*\\d[\\d. ]{3,18}(\\s*-\\s*\\d)?\\s*",
            message = "Escribe el NIT con números y, si aplica, el dígito de verificación. Ej.: 900123456-7")
    private String nit;

    @NotBlank(message = "Escribe el usuario con el que la empresa iniciará sesión.")
    @Pattern(regexp = "\\s*|\\s*[A-Za-z0-9._-]{3,30}\\s*",
            message = "Usa de 3 a 30 caracteres: letras sin tildes, números, punto, guion o guion bajo, sin espacios.")
    private String username;

    @NotNull(message = "Escribe una contraseña.")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    @Pattern(regexp = "(?s)|.*\\S.*", message = "La contraseña no puede ser solo espacios.")
    private String password;

    private String confirmacion;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNit() {
        return nit;
    }

    public void setNit(String nit) {
        this.nit = nit;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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
