package com.stocktag.repository;

import java.time.LocalDate;

/**
 * Fila de la tabla de empresas del panel /admin: datos de la empresa, el usuario con el que
 * inicia sesión y cuántos productos tiene (solo el número; el administrador no ve inventarios).
 */
public record EmpresaResumen(Long id,
                             String nombre,
                             String nit,
                             LocalDate fechaAlta,
                             Boolean activa,
                             String username,
                             Long totalProductos) {
}
