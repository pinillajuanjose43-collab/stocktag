package com.stocktag.controller;

import com.stocktag.model.TipoMovimiento;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

/**
 * Filtros opcionales del historial de movimientos (GET /movimientos?tipo=&desde=&hasta=&pagina=).
 * No lleva empresa: la empresa siempre sale del usuario autenticado en InventarioService.
 */
public class FiltroMovimientosForm {

    private TipoMovimiento tipo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate desde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hasta;

    /** Página empezando en 1. */
    private Integer pagina;

    public boolean tieneFiltros() {
        return tipo != null || desde != null || hasta != null;
    }

    /** URL de otra página conservando los filtros activos (sin parámetros vacíos). */
    public String urlPagina(int numero) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/movimientos");
        if (tipo != null) {
            url.queryParam("tipo", tipo.name());
        }
        if (desde != null) {
            url.queryParam("desde", desde.toString());
        }
        if (hasta != null) {
            url.queryParam("hasta", hasta.toString());
        }
        if (numero > 1) {
            url.queryParam("pagina", numero);
        }
        return url.build().toUriString();
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimiento tipo) {
        this.tipo = tipo;
    }

    public LocalDate getDesde() {
        return desde;
    }

    public void setDesde(LocalDate desde) {
        this.desde = desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public void setHasta(LocalDate hasta) {
        this.hasta = hasta;
    }

    public Integer getPagina() {
        return pagina;
    }

    public void setPagina(Integer pagina) {
        this.pagina = pagina;
    }
}
