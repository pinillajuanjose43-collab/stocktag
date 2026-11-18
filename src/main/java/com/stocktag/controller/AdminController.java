package com.stocktag.controller;

import com.stocktag.model.Empresa;
import com.stocktag.repository.EmpresaResumen;
import com.stocktag.service.EmpresaException;
import com.stocktag.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Panel del ADMIN_PLATAFORMA (/admin/**, protegido en SecurityConfig). Controller delgado:
 * las reglas están en EmpresaService; aquí solo se arma la vista y los mensajes.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String VISTA_FORMULARIO = "admin/empresa-form";

    private final EmpresaService empresaService;

    public AdminController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    /**
     * Tabla de empresas. Con {@code ?confirmar=<id>} muestra, en la misma página y sin JavaScript,
     * el panel para confirmar la desactivación de esa empresa.
     */
    @GetMapping
    public String empresas(@RequestParam(name = "confirmar", required = false) Long confirmar, Model model) {
        List<EmpresaResumen> empresas = empresaService.listarEmpresas();
        model.addAttribute("empresas", empresas);
        model.addAttribute("totalActivas", empresas.stream().filter(EmpresaResumen::activa).count());
        if (confirmar != null) {
            empresas.stream()
                    .filter(e -> e.id().equals(confirmar) && e.activa())
                    .findFirst()
                    .ifPresent(e -> model.addAttribute("porDesactivar", e));
        }
        return "admin/empresas";
    }

    @PostMapping("/empresas/{id}/desactivar")
    public String desactivar(@PathVariable Long id, RedirectAttributes redirect) {
        return cambiarEstado(id, false, redirect);
    }

    @PostMapping("/empresas/{id}/activar")
    public String activar(@PathVariable Long id, RedirectAttributes redirect) {
        return cambiarEstado(id, true, redirect);
    }

    @GetMapping("/empresas/nueva")
    public String nueva(Model model) {
        model.addAttribute("empresaForm", new EmpresaForm());
        return VISTA_FORMULARIO;
    }

    @PostMapping("/empresas")
    public String registrar(@Valid @ModelAttribute("empresaForm") EmpresaForm form, BindingResult resultado,
                            RedirectAttributes redirect) {
        if (!resultado.hasErrors()) {
            try {
                Empresa empresa = empresaService.registrarEmpresa(
                        form.getNombre(), form.getNit(), form.getUsername(), form.getPassword());
                redirect.addFlashAttribute("exito", "Empresa \"" + empresa.getNombre()
                        + "\" registrada. Ya puede iniciar sesión con el usuario \"" + form.getUsername().trim() + "\".");
                return "redirect:/admin";
            } catch (EmpresaException e) {
                if (e.getCampo() != null) {
                    resultado.rejectValue(e.getCampo(), "empresa", e.getMessage());
                } else {
                    resultado.reject("empresa", e.getMessage());
                }
            }
        }
        // Las contraseñas nunca se devuelven en el HTML: hay que escribirlas de nuevo.
        form.setPassword(null);
        form.setConfirmacion(null);
        return VISTA_FORMULARIO;
    }

    private String cambiarEstado(Long id, boolean activa, RedirectAttributes redirect) {
        try {
            Empresa empresa = empresaService.cambiarEstadoEmpresa(id, activa);
            redirect.addFlashAttribute("exito", activa
                    ? "Empresa \"" + empresa.getNombre() + "\" activada. Su usuario ya puede iniciar sesión."
                    : "Empresa \"" + empresa.getNombre() + "\" desactivada. Su usuario perdió el acceso, "
                    + "incluida cualquier sesión abierta.");
        } catch (EmpresaException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }
}
