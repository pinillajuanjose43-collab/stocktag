package com.stocktag.repository;

import com.stocktag.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);

    /** Todas las empresas con su usuario EMPRESA y su número de productos, ordenadas por nombre. */
    @Query("""
            select new com.stocktag.repository.EmpresaResumen(
                e.id, e.nombre, e.nit, e.fechaAlta, e.activa, u.username,
                (select count(p) from Producto p where p.empresa = e))
            from Empresa e
            left join Usuario u on u.empresa = e and u.rol = com.stocktag.model.Rol.EMPRESA
            order by lower(e.nombre), e.id
            """)
    List<EmpresaResumen> listarResumen();
}
