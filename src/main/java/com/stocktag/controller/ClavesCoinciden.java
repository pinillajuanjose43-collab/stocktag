package com.stocktag.controller;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La contraseña y su confirmación deben ser iguales. El error se muestra debajo del campo
 * "confirmacion" (no como error general del formulario).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ClavesCoincidenValidator.class)
public @interface ClavesCoinciden {

    String message() default "Las contraseñas no coinciden.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
