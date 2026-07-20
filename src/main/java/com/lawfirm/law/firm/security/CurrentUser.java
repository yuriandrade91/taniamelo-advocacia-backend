package com.lawfirm.law.firm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Acesso estático ao usuário autenticado da requisição corrente. Centraliza o
 * helper que antes era copiado em cada service.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Id do usuário autenticado, ou null (chamadas de sistema/testes). */
    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
