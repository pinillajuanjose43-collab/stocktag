package com.stocktag.service;

/**
 * Se publica cuando una empresa queda desactivada. La capa de seguridad lo escucha después
 * del commit para cerrar las sesiones abiertas de sus usuarios (ver CierreSesionesListener).
 */
public record EmpresaDesactivadaEvent(Long empresaId) {
}
