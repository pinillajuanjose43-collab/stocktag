package com.stocktag.controller;

import com.stocktag.model.Empresa;
import com.stocktag.model.Usuario;
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
import java.util.Optional;

/**
 * Panel del ADMIN_PLATAFORMA (/admin/**, protegido en SecurityConfig). Controller delgado:
 * las reglas están en EmpresaService; aquí solo se arma la vista y los mensajes.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String VISTA_FORMULARIO = "admin/empresa-form";
    private static final String VISTA_CLAVE = "admin/clave-form";

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

    /** Formulario para restablecer la contraseña del usuario de una empresa. */
    @GetMapping("/empresas/{id}/clave")
    public String formularioClave(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        Optional<EmpresaResumen> empresa = buscarConUsuario(id);
        if (empresa.isEmpty()) {
            redirect.addFlashAttribute("error", "La empresa indicada no existe o no tiene un usuario para iniciar sesión.");
            return "redirect:/admin";
        }
        model.addAttribute("empresa", empresa.get());
        model.addAttribute("claveForm", new ClaveForm());
        return VISTA_CLAVE;
    }

    @PostMapping("/empresas/{id}/clave")
    public String restablecerClave(@PathVariable Long id, @Valid @ModelAttribute("claveForm") ClaveForm form,
                                   BindingResult resultado, Model model, RedirectAttributes redirect) {
        if (!resultado.hasErrors()) {
            try {
                Usuario usuario = empresaService.restablecerClave(id, form.getPassword());
                redirect.addFlashAttribute("exito", "Contraseña de \"" + usuario.getEmpresa().getNombre()
                        + "\" restablecida. Comunícale la contraseña nueva al usuario \"" + usuario.getUsername()
                        + "\"; sus sesiones abiertas se cerraron.");
                return "redirect:/admin";
            } catch (EmpresaException e) {
                if (e.getCampo() == null) {
                    // La empresa no existe o no tiene usuario: el formulario no sirve de nada.
                    redirect.addFlashAttribute("error", e.getMessage());
                    return "redirect:/admin";
                }
                resultado.rejectValue(e.getCampo(), "empresa", e.getMessage());
            }
        }
        Optional<EmpresaResumen> empresa = buscarConUsuario(id);
        if (empresa.isEmpty()) {
            redirect.addFlashAttribute("error", "La empresa indicada no existe o no tiene un usuario para iniciar sesión.");
            return "redirect:/admin";
        }
        model.addAttribute("empresa", empresa.get());
        // Las contraseñas nunca se devuelven en el HTML: hay que escribirlas de nuevo.
        form.setPassword(null);
        form.setConfirmacion(null);
        return VISTA_CLAVE;
    }

    /** Fila de la tabla de esa empresa, solo si tiene un usuario al que cambiarle la contraseña. */
    private Optional<EmpresaResumen> buscarConUsuario(Long id) {
        return empresaService.listarEmpresas().stream()
                .filter(e -> e.id().equals(id) && e.username() != null)
                .findFirst();
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
