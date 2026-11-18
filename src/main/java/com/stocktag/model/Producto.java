package com.stocktag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Producto del inventario de una empresa. El código de barras es único por empresa:
 * dos empresas distintas pueden tener un producto con el mismo código.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_producto_empresa_codigo",
        columnNames = {"empresa_id", "codigo_barras"}))
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El código de barras es obligatorio")
    @Column(name = "codigo_barras", nullable = false)
    private String codigoBarras;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(nullable = false)
    private String nombre;

    @Min(value = 0, message = "La cantidad no puede ser negativa")
    @Column(nullable = false)
    private int cantidad;

    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    @Column(nullable = false)
    private int stockMinimo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    /** Bloqueo optimista: evita que dos movimientos simultáneos pisen la cantidad. */
    @Version
    private Long version;

    protected Producto() {
    }

    public Producto(String codigoBarras, String nombre, int stockMinimo, Empresa empresa) {
        this.codigoBarras = codigoBarras;
        this.nombre = nombre;
        this.cantidad = 0;
        this.stockMinimo = stockMinimo;
        this.empresa = empresa;
    }

    /** Alerta de stock bajo cuando la cantidad es menor o igual al stock mínimo. */
    public boolean tieneStockBajo() {
        return cantidad <= stockMinimo;
    }

    public Long getId() {
        return id;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Solo InventarioService modifica la cantidad, siempre a través de un movimiento. */
    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(int stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public Empresa getEmpresa() {
        return empresa;
    }
}
