package com.lawfirm.law.firm.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Diz ao Hibernate qual é o tenant (schema) corrente. Lê o {@link TenantContext}.
 *
 * <p>Sem tenant (ou com um que não está ativo no catálogo) a sessão vai para {@code public}, onde
 * não existe tabela de escritório: um acesso indevido falha com "relation does not exist" em vez de
 * ler, em silêncio, os dados de algum escritório padrão. O que vive em {@code public} (catálogo,
 * agentes de suporte) continua acessível, pois essas entidades são qualificadas com o schema.
 * {@code validateExistingCurrentSessions=true} garante que o Hibernate reuse a sessão só se o
 * tenant bater.
 */
@Component
public class SchemaCurrentTenantResolver implements CurrentTenantIdentifierResolver<String> {

    static final String NO_TENANT_SCHEMA = "public";

    private final ActiveTenantSchemas activeSchemas;

    public SchemaCurrentTenantResolver(ActiveTenantSchemas activeSchemas) {
        this.activeSchemas = activeSchemas;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String current = TenantContext.get();
        return (current != null && activeSchemas.isKnown(current)) ? current : NO_TENANT_SCHEMA;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
