package com.stocktag.controller;

import com.stocktag.model.Movimiento;
import com.stocktag.model.TipoMovimiento;
import com.stocktag.service.InventarioException;
import com.stocktag.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Dashboard con el flujo de escaneo. Controller delgado: la lógica y la empresa
 * salen de InventarioService; aquí solo se arma la vista y los mensajes.
 */
@Controller
public class InventarioController {

    private final InventarioService inventarioService;
    private final MessageSource messageSource;

    public InventarioController(InventarioService inventarioService, MessageSource messageSource) {
        this.inventarioService = inventarioService;
        this.messageSource = messageSource;
    }

    /** El lector escribe el código en el input y envía con Enter: GET /?codigo=... */
    @GetMapping("/")
    public String inicio(@RequestParam(name = "codigo", required = false) String codigo, Model model) {
        model.addAttribute("seccion", "inicio");
        model.addAttribute("alertas", inventarioService.verificarAlertasStock());
        if (codigo != null && !codigo.isBlank()) {
            String codigoBuscado = codigo.trim();
            model.addAttribute("codigoBuscado", codigoBuscado);
            inventarioService.buscarPorCodigoBarras(codigoBuscado)
                    .ifPresent(producto -> model.addAttribute("producto", producto));
        }
        return "inventario/inicio";
    }

    /**
     * Registra una ENTRADA o SALIDA y vuelve al dashboard (patrón POST-redirect-GET).
     * Los errores del service se muestran como alerta, nunca como página de error.
     */
    @PostMapping("/movimientos")
    public String registrarMovimiento(@Valid MovimientoForm form, BindingResult resultado,
                                      RedirectAttributes redirect) {
        if (form.getCodigoBarras() != null && !form.getCodigoBarras().isBlank()) {
            redirect.addAttribute("codigo", form.getCodigoBarras().trim());
        }
        if (resultado.hasErrors()) {
            redirect.addFlashAttribute("error", primerError(resultado));
            return "redirect:/";
        }
        try {
            Movimiento movimiento = inventarioService.registrarMovimiento(
                    form.getProductoId(), form.getTipo(), form.getCantidad());
            redirect.addFlashAttribute("exito", mensajeExito(movimiento));
        } catch (InventarioException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    private String primerError(BindingResult resultado) {
        return messageSource.getMessage(resultado.getAllErrors().get(0), LocaleContextHolder.getLocale());
    }

    private static String mensajeExito(Movimiento movimiento) {
        boolean entrada = movimiento.getTipo() == TipoMovimiento.ENTRADA;
        return (entrada ? "Entrada registrada: +" : "Salida registrada: -") + movimiento.getCantidad()
                + " de " + movimiento.getProducto().getNombre()
                + ". Stock actual: " + movimiento.getProducto().getCantidad() + ".";
    }
}
