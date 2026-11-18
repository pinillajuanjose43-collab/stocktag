package com.stocktag.repository;

import com.stocktag.model.Movimiento;
import com.stocktag.model.TipoMovimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Los movimientos se filtran por la empresa dueña del producto. */
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    @EntityGraph(attributePaths = "producto")
    List<Movimiento> findByProductoEmpresaIdOrderByFechaDesc(Long empresaId);

    @EntityGraph(attributePaths = "producto")
    List<Movimiento> findByProductoIdAndProductoEmpresaIdOrderByFechaDesc(Long productoId, Long empresaId);

    /**
     * Historial de movimientos de una empresa, del más reciente al más antiguo.
     * Los filtros son opcionales: si {@code tipo}, {@code desde} o {@code hasta} llegan en null, no se aplican.
     */
    @EntityGraph(attributePaths = "producto")
    @Query(value = """
            select m from Movimiento m
            where m.producto.empresa.id = :empresaId
              and (:tipo is null or m.tipo = :tipo)
              and (cast(:desde as localdatetime) is null or m.fecha >= :desde)
              and (cast(:hasta as localdatetime) is null or m.fecha <= :hasta)
            order by m.fecha desc, m.id desc
            """,
            countQuery = """
            select count(m) from Movimiento m
            where m.producto.empresa.id = :empresaId
              and (:tipo is null or m.tipo = :tipo)
              and (cast(:desde as localdatetime) is null or m.fecha >= :desde)
              and (cast(:hasta as localdatetime) is null or m.fecha <= :hasta)
            """)
    Page<Movimiento> buscarHistorial(@Param("empresaId") Long empresaId,
                                     @Param("tipo") TipoMovimiento tipo,
                                     @Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta,
                                     Pageable pageable);
}
