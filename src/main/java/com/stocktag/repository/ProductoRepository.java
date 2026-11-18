package com.stocktag.repository;

import com.stocktag.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Todas las consultas reciben el id de la empresa: nunca se busca un producto sin filtrar por empresa. */
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByEmpresaIdOrderByNombreAsc(Long empresaId);

    Optional<Producto> findByIdAndEmpresaId(Long id, Long empresaId);

    Optional<Producto> findByEmpresaIdAndCodigoBarras(Long empresaId, String codigoBarras);

    boolean existsByEmpresaIdAndCodigoBarras(Long empresaId, String codigoBarras);

    @Query("""
            select p from Producto p
            where p.empresa.id = :empresaId and p.cantidad <= p.stockMinimo
            order by p.nombre asc
            """)
    List<Producto> buscarConStockBajo(@Param("empresaId") Long empresaId);
}
