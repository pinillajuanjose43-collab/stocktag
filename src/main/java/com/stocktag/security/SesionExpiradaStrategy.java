package com.stocktag.security;

import com.stocktag.model.Empresa;
import com.stocktag.repository.EmpresaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A dónde va una sesión que CierreSesionesListener marcó como expirada. Hay dos motivos y se
 * distinguen por el estado actual de la empresa:
 * <ul>
 *     <li>empresa desactivada: /login?desactivada</li>
 *     <li>empresa activa: el administrador restableció la contraseña, /login?claveRestablecida</li>
 * </ul>
 * Spring Security ya cerró la sesión antes de llamar a esta clase.
 */
@Component
public class SesionExpiradaStrategy implements SessionInformationExpiredStrategy {

    private final EmpresaRepository empresaRepository;
    private final RedirectStrategy redireccion = new DefaultRedirectStrategy();

    public SesionExpiradaStrategy(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent evento) throws IOException {
        HttpServletRequest request = evento.getRequest();
        redireccion.sendRedirect(request, evento.getResponse(),
                "/login?" + motivo(evento.getSessionInformation().getPrincipal()));
    }

    private String motivo(Object principal) {
        if (principal instanceof UsuarioAutenticado usuario && usuario.getEmpresaId() != null) {
            boolean activa = empresaRepository.findById(usuario.getEmpresaId())
                    .map(Empresa::isActiva)
                    .orElse(false);
            if (activa) {
                return "claveRestablecida";
            }
        }
        return "desactivada";
    }
}
