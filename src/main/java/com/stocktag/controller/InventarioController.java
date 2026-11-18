package com.stocktag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InventarioController {

    @GetMapping("/")
    public String inicio() {
        return "inventario/inicio";
    }
}
