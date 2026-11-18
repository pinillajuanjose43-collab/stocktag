package com.stocktag.security;

import com.stocktag.model.Empresa;
import com.stocktag.model.Rol;
import com.stocktag.model.Usuario;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Principal de Spring Security. Guarda el id de la empresa para que los services
 * la obtengan del usuario autenticado (REGLA DE ORO), nunca de la URL o del formulario.
 */
public class UsuarioAutenticado extends User {

    private final Rol rol;
    private final Long empresaId;
    private final String nombreEmpresa;

    public UsuarioAutenticado(Usuario usuario) {
        super(usuario.getUsername(),
                usuario.getPassword(),
                estaHabilitado(usuario),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
        Empresa empresa = usuario.getEmpresa();
        this.rol = usuario.getRol();
        this.empresaId = empresa != null ? empresa.getId() : null;
        this.nombreEmpresa = empresa != null ? empresa.getNombre() : null;
    }

    /** Un usuario de una empresa desactivada no puede iniciar sesión. */
    private static boolean estaHabilitado(Usuario usuario) {
        if (usuario.getRol() == Rol.ADMIN_PLATAFORMA) {
            return true;
        }
        return usuario.getEmpresa() != null && usuario.getEmpresa().isActiva();
    }

    public Rol getRol() {
        return rol;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public String getNombreEmpresa() {
        return nombreEmpresa;
    }
}
