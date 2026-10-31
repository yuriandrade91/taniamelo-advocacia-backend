package com.lawfirm.law.firm.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acesso estático ao usuário autenticado da requisição corrente. Centraliza o helper que antes era
 * copiado em cada service.
 */
public final class CurrentUser {

    private CurrentUser() {}

    /** Id do usuário autenticado, ou null (chamadas de sistema/testes/sessão de suporte). */
    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    /** Se o usuário autenticado tem o papel, para quem recorta dados em vez de recusar a rota. */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                        .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    /**
     * Id do agente de suporte quando a requisição é uma sessão de suporte (impersonation), ou null.
     * A auditoria usa isto para atribuir a ação a quem de fato a executou.
     */
    public static UUID actingSupportId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getActingSupportUserId();
        }
        return null;
    }
}
