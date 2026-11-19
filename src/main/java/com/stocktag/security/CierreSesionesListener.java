package com.stocktag.security;

import com.stocktag.service.ClaveRestablecidaEvent;
import com.stocktag.service.EmpresaDesactivadaEvent;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cuando se desactiva una empresa o se restablece la contraseña de un usuario, marca como
 * expiradas sus sesiones abiertas. Spring Security (ConcurrentSessionFilter) revisa ese estado
 * en cada petición: la siguiente petición de esa sesión cierra la sesión y va al login con el
 * aviso que corresponda (ver SesionExpiradaStrategy).
 *
 * <p>Corre después del commit: si la transacción fallara, nada habría cambiado y no tendría
 * sentido sacar a nadie; y así nadie puede volver a entrar "en medio" con el dato anterior.
 */
@Component
public class CierreSesionesListener {

    private final SessionRegistry sessionRegistry;

    public CierreSesionesListener(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alDesactivarEmpresa(EmpresaDesactivadaEvent evento) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UsuarioAutenticado usuario
                    && evento.empresaId().equals(usuario.getEmpresaId())) {
                sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRestablecerClave(ClaveRestablecidaEvent evento) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UsuarioAutenticado usuario
                    && evento.username().equals(usuario.getUsername())) {
                sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
            }
        }
    }
}
