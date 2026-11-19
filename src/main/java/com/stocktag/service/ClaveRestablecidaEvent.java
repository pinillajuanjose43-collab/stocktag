package com.stocktag.service;

/**
 * Se publica cuando el administrador restablece la contraseña de un usuario. La capa de
 * seguridad lo escucha después del commit para cerrar sus sesiones abiertas (ver CierreSesionesListener).
 */
public record ClaveRestablecidaEvent(String username) {
}
