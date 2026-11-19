package com.stocktag.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

import java.io.IOException;

/**
 * Acceso denegado a un usuario con sesión.
 * <ul>
 *   <li>El administrador de la plataforma que abre la raíz ({@code GET /}, por ejemplo escribiendo
 *       localhost:8080) va a su panel {@code /admin} en vez de ver un error: no tiene inventario.</li>
 *   <li>En cualquier otro caso (las demás rutas de inventario, /admin para una empresa, CSRF inválido)
 *       se responde 403 como siempre, con la página {@code error/403.html}.</li>
 * </ul>
 */
public class AccesoDenegadoHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler porDefecto = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (esRaiz(request) && "GET".equals(request.getMethod()) && esAdministrador()) {
            response.sendRedirect(request.getContextPath() + "/admin");
            return;
        }
        porDefecto.handle(request, response, accessDeniedException);
    }

    private static boolean esRaiz(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return ruta.isEmpty() || ruta.equals("/");
    }

    private static boolean esAdministrador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN_PLATAFORMA"));
    }
}
