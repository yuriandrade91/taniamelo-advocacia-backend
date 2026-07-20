package com.lawfirm.law.firm.model;

/**
 * Papéis de acesso ao sistema. Mantido simples por ora (Fase 1); regras de autorização por papel
 * podem ser adicionadas depois via @PreAuthorize / method security sem precisar alterar o modelo.
 */
public enum Role {
    ADMIN,
    LAWYER,
    STAFF
}
