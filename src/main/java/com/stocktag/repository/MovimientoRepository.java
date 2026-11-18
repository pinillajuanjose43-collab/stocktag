package com.stocktag.repository;

import com.stocktag.model.Movimiento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Los movimientos se filtran por la empresa dueña del producto. */
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    @EntityGraph(attributePaths = "producto")
    List<Movimiento> findByProductoEmpresaIdOrderByFechaDesc(Long empresaId);

    @EntityGraph(attributePaths = "producto")
    List<Movimiento> findByProductoIdAndProductoEmpresaIdOrderByFechaDesc(Long productoId, Long empresaId);
}
