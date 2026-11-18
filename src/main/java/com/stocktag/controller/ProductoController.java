package com.stocktag.controller;

import com.stocktag.model.Producto;
import com.stocktag.service.InventarioException;
import com.stocktag.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Lista y formulario de productos. Controller delgado: todo pasa por InventarioService. */
@Controller
@RequestMapping("/productos")
public class ProductoController {

    private static final String VISTA_FORMULARIO = "inventario/producto-form";

    private final InventarioService inventarioService;

    public ProductoController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @ModelAttribute("seccion")
    public String seccion() {
        return "productos";
    }

    /** Lista completa o, con {@code ?filtro=bajo}, solo los productos con stock bajo. */
    @GetMapping
    public String lista(@RequestParam(name = "filtro", required = false) String filtro, Model model) {
        List<Producto> todos = inventarioService.consultarStock();
        List<Producto> conStockBajo = inventarioService.verificarAlertasStock();
        boolean soloBajo = "bajo".equals(filtro);
        model.addAttribute("soloBajo", soloBajo);
        model.addAttribute("totalProductos", todos.size());
        model.addAttribute("totalStockBajo", conStockBajo.size());
        model.addAttribute("productos", soloBajo ? conStockBajo : todos);
        return "inventario/productos";
    }

    /** Formulario vacío o con el código ya llenado cuando viene del escaneo. */
    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(name = "codigo", required = false) String codigo, Model model) {
        ProductoForm form = new ProductoForm();
        if (codigo != null && !codigo.isBlank()) {
            form.setCodigoBarras(codigo.trim());
        }
        model.addAttribute("productoForm", form);
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("productoForm") ProductoForm form, BindingResult resultado,
                        Model model, RedirectAttributes redirect) {
        if (resultado.hasErrors()) {
            return VISTA_FORMULARIO;
        }
        try {
            Producto producto = inventarioService.crearProducto(
                    form.getCodigoBarras(), form.getNombre(), form.getStockMinimo(), form.getStockInicial());
            redirect.addAttribute("codigo", producto.getCodigoBarras());
            redirect.addFlashAttribute("exito", "Producto creado: " + producto.getNombre()
                    + ". Stock actual: " + producto.getCantidad() + ".");
            return "redirect:/";
        } catch (InventarioException e) {
            model.addAttribute("error", e.getMessage());
            return VISTA_FORMULARIO;
        }
    }
}
