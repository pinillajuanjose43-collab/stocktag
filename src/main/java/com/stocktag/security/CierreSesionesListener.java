package com.stocktag.security;

import com.stocktag.service.EmpresaDesactivadaEvent;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cuando se desactiva una empresa, marca como expiradas todas las sesiones abiertas de sus
 * usuarios. Spring Security (ConcurrentSessionFilter) revisa ese estado en cada petición:
 * la siguiente petición de esa sesión cierra la sesión y redirige a /login?desactivada.
 *
 * <p>Corre después del commit: si la transacción fallara, la empresa seguiría activa y no
 * tendría sentido sacar a nadie; y así nadie puede volver a entrar "en medio" leyendo
 * todavía activa=true.
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
}
