package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenancyProperties: whitelist de schemas aceitos")
class TenancyPropertiesTest {

    @Test
    @DisplayName("defaults sensatos quando nada é configurado")
    void hasSaneDefaults() {
        TenancyProperties properties = new TenancyProperties();
        assertEquals("tenant_tania", properties.getDefaultSchema());
        assertEquals(List.of("tenant_tania"), properties.getSchemas());
        assertEquals("X-Tenant-Id", properties.getHeaderName());
    }

    @Test
    @DisplayName("isKnown aceita apenas schemas configurados")
    void isKnownOnlyForConfiguredSchemas() {
        TenancyProperties properties = new TenancyProperties();
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));

        assertTrue(properties.isKnown("tenant_tania"));
        assertTrue(properties.isKnown("tenant_demo"));
        assertFalse(properties.isKnown("tenant_invasor"));
        assertFalse(properties.isKnown("public"));
    }

    @Test
    @DisplayName("isKnown é case-sensitive e rejeita null - defesa contra injeção via header")
    void isKnownRejectsNullAndWrongCase() {
        TenancyProperties properties = new TenancyProperties();
        properties.setSchemas(List.of("tenant_tania"));

        assertFalse(properties.isKnown(null));
        assertFalse(properties.isKnown("TENANT_TANIA"));
        assertFalse(properties.isKnown("tenant_tania; DROP TABLE clients"));
        assertFalse(properties.isKnown(""));
    }

    @Test
    @DisplayName("setters de configuração são refletidos")
    void settersApply() {
        TenancyProperties properties = new TenancyProperties();
        properties.setDefaultSchema("tenant_x");
        properties.setHeaderName("X-Escritorio");
        properties.setSchemas(List.of("tenant_x"));

        assertEquals("tenant_x", properties.getDefaultSchema());
        assertEquals("X-Escritorio", properties.getHeaderName());
        assertEquals(List.of("tenant_x"), properties.getSchemas());
    }
}
