package com.lawfirm.law.firm.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Diz ao Hibernate qual é o tenant (schema) corrente. Lê o {@link TenantContext}; sem tenant na
 * requisição, cai no schema default (boot, validação de schema, jobs). {@code
 * validateExistingCurrentSessions=true} garante que o Hibernate reuse a sessão só se o tenant
 * bater.
 */
@Component
public class SchemaCurrentTenantResolver implements CurrentTenantIdentifierResolver<String> {

    private final TenancyProperties properties;
    private final ActiveTenantSchemas activeSchemas;

    public SchemaCurrentTenantResolver(
            TenancyProperties properties, ActiveTenantSchemas activeSchemas) {
        this.properties = properties;
        this.activeSchemas = activeSchemas;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String current = TenantContext.get();
        return (current != null && activeSchemas.isKnown(current))
                ? current
                : properties.getDefaultSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
