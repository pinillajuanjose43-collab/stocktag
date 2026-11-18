package com.stocktag.controller;

import com.stocktag.service.AlertaStock;
import com.stocktag.service.InventarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Alertas de stock bajo. Controller delgado: los datos y la empresa salen de InventarioService. */
@Controller
public class AlertaController {

    private final InventarioService inventarioService;

    public AlertaController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/alertas")
    public String alertas(Model model) {
        List<AlertaStock> alertas = inventarioService.listarAlertasStock();
        long agotados = alertas.stream().filter(AlertaStock::agotado).count();
        model.addAttribute("seccion", "alertas");
        model.addAttribute("alertas", alertas);
        model.addAttribute("totalAgotados", agotados);
        model.addAttribute("totalStockBajo", alertas.size() - agotados);
        return "inventario/alertas";
    }
}
