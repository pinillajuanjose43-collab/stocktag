package com.stocktag.controller;

import com.stocktag.service.InventarioService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Descarga del reporte de inventario en CSV. El contenido y el nombre salen de InventarioService. */
@RestController
public class ReporteController {

    static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final InventarioService inventarioService;

    public ReporteController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/reporte.csv")
    public ResponseEntity<byte[]> descargarReporte() {
        byte[] csv = inventarioService.generarReporteCSV();
        ContentDisposition adjunto = ContentDisposition.attachment()
                .filename(inventarioService.nombreArchivoReporte())
                .build();
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, adjunto.toString())
                .body(csv);
    }
}
