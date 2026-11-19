package com.stocktag.repository;

import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByRol(Rol rol);

    /** "Tienda" y "tienda" cuentan como el mismo usuario para evitar confusiones al iniciar sesión. */
    boolean existsByUsernameIgnoreCase(String username);

    /** Usuario con el que inicia sesión una empresa (cada empresa tiene uno con rol EMPRESA). */
    Optional<Usuario> findFirstByEmpresaIdAndRol(Long empresaId, Rol rol);
}
