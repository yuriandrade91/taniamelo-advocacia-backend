package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchemaCurrentTenantResolver: qual schema o Hibernate usa nesta requisição")
class SchemaCurrentTenantResolverTest {

    private SchemaCurrentTenantResolver resolver;

    @BeforeEach
    void setUp() {
        TenancyProperties properties = new TenancyProperties();
        properties.setDefaultSchema("tenant_tania");
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));
        resolver = new SchemaCurrentTenantResolver(properties);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("sem tenant na requisição cai no schema default")
    void fallsBackToDefault() {
        TenantContext.clear();
        assertEquals("tenant_tania", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    @DisplayName("tenant conhecido no contexto é usado")
    void usesKnownTenantFromContext() {
        TenantContext.set("tenant_demo");
        assertEquals("tenant_demo", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    @DisplayName("schema desconhecido no contexto é descartado em favor do default")
    void unknownSchemaFallsBackToDefault() {
        TenantContext.set("tenant_invasor");
        assertEquals("tenant_tania", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    @DisplayName("tentativa de injeção via contexto não passa")
    void injectionAttemptFallsBackToDefault() {
        TenantContext.set("public; DROP SCHEMA tenant_tania CASCADE");
        assertEquals("tenant_tania", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    @DisplayName("valida sessões existentes para não reusar conexão de outro tenant")
    void validatesExistingSessions() {
        assertTrue(resolver.validateExistingCurrentSessions());
    }
}
