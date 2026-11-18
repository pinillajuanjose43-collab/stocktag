package com.stocktag.controller;

import com.stocktag.service.InventarioService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Datos que necesita la barra de navegación en todas las vistas de inventario:
 * el número de alertas de stock bajo de la empresa autenticada (contador del enlace Alertas).
 * Solo aplica a los controllers de inventario (rol EMPRESA), no al panel /admin ni al login.
 */
@ControllerAdvice(assignableTypes = {
        InventarioController.class,
        ProductoController.class,
        AlertaController.class,
        HistorialController.class
})
public class NavegacionAdvice {

    private final InventarioService inventarioService;

    public NavegacionAdvice(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @ModelAttribute("totalAlertas")
    public long totalAlertas() {
        return inventarioService.contarAlertasStock();
    }
}
