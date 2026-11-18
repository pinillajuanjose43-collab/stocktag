package com.stocktag.controller;

import com.stocktag.model.Movimiento;
import com.stocktag.service.InventarioException;
import com.stocktag.service.InventarioService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Historial de movimientos (GET /movimientos). El registro de movimientos (POST /movimientos)
 * sigue en InventarioController. Controller delgado: filtros, paginación y empresa se resuelven
 * en InventarioService.
 */
@Controller
public class HistorialController {

    private final InventarioService inventarioService;
    private final MessageSource messageSource;

    public HistorialController(InventarioService inventarioService, MessageSource messageSource) {
        this.inventarioService = inventarioService;
        this.messageSource = messageSource;
    }

    @GetMapping("/movimientos")
    public String historial(@ModelAttribute("filtro") FiltroMovimientosForm filtro, BindingResult resultado,
                            Model model) {
        model.addAttribute("seccion", "movimientos");
        // Un valor mal escrito (fecha o tipo) se informa y ese filtro no se aplica; la página se ignora en silencio.
        resultado.getFieldErrors().stream()
                .filter(error -> !"pagina".equals(error.getField()))
                .findFirst()
                .ifPresent(error -> model.addAttribute("error",
                        messageSource.getMessage(error, LocaleContextHolder.getLocale())));
        try {
            int pagina = filtro.getPagina() != null ? filtro.getPagina() : 1;
            Page<Movimiento> movimientos = inventarioService.consultarMovimientos(
                    filtro.getTipo(), filtro.getDesde(), filtro.getHasta(), pagina);
            model.addAttribute("movimientos", movimientos);
        } catch (InventarioException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorRango", true);
            model.addAttribute("movimientos", Page.<Movimiento>empty());
        }
        return "inventario/movimientos";
    }
}
