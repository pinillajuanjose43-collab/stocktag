package com.stocktag.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos del formulario de producto. La empresa NO viaja en el formulario: la pone el service. */
public class ProductoForm {

    @NotBlank(message = "Escribe o escanea el código de barras.")
    @Size(max = 64, message = "El código de barras admite máximo 64 caracteres.")
    private String codigoBarras;

    @NotBlank(message = "Escribe el nombre del producto.")
    @Size(max = 120, message = "El nombre admite máximo 120 caracteres.")
    private String nombre;

    @NotNull(message = "Indica el stock mínimo (puede ser 0).")
    @Min(value = 0, message = "El stock mínimo no puede ser negativo.")
    @Max(value = 1_000_000, message = "El stock mínimo no puede superar 1.000.000.")
    private Integer stockMinimo = 0;

    /** Opcional. Si es mayor que cero se registra como una ENTRADA. */
    @Min(value = 0, message = "El stock inicial no puede ser negativo.")
    @Max(value = 1_000_000, message = "El stock inicial no puede superar 1.000.000.")
    private Integer stockInicial;

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(Integer stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public Integer getStockInicial() {
        return stockInicial;
    }

    public void setStockInicial(Integer stockInicial) {
        this.stockInicial = stockInicial;
    }
}
