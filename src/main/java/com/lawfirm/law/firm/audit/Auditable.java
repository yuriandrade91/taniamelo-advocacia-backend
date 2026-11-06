package com.lawfirm.law.firm.audit;

import java.util.UUID;

/**
 * Marca uma entidade como coberta por {@link AuditLogListener}: toda criação, alteração ou remoção
 * gera automaticamente uma linha em audit_log com o usuário autenticado que executou a ação ({@code
 * CurrentUser.id()}), sem depender de nenhum service lembrar de fazer isso manualmente.
 *
 * <p>Para uma entidade nova entrar na trilha de auditoria, basta: implementar esta interface e
 * anotar a classe com {@code @EntityListeners(AuditLogListener.class)}.
 */
public interface Auditable {

    UUID getId();
}
