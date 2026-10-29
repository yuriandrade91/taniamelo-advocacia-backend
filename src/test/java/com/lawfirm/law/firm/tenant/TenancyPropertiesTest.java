package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenancyProperties: configuração de multi-tenancy")
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
