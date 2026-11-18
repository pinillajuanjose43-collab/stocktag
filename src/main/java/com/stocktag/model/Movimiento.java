package com.stocktag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Entrada o salida de unidades de un producto. Siempre guarda fecha y hora. */
@Entity
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    @Min(value = 1, message = "La cantidad debe ser mayor que cero")
    @Column(nullable = false)
    private int cantidad;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha;

    protected Movimiento() {
    }

    public Movimiento(Producto producto, TipoMovimiento tipo, int cantidad) {
        this(producto, tipo, cantidad, LocalDateTime.now());
    }

    /** Movimiento con una fecha dada. Solo lo usan los datos de ejemplo para armar un historial de días pasados. */
    public Movimiento(Producto producto, TipoMovimiento tipo, int cantidad, LocalDateTime fecha) {
        this.producto = producto;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Producto getProducto() {
        return producto;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public int getCantidad() {
        return cantidad;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
