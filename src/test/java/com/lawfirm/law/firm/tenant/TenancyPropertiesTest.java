package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenancyProperties: configuração de multi-tenancy")
class TenancyPropertiesTest {

    @Test
    @DisplayName("defaults sensatos quando nada é configurado")
    void hasSaneDefaults() {
        TenancyProperties properties = new TenancyProperties();
        assertEquals("X-Tenant-Id", properties.getHeaderName());
    }

    @Test
    @DisplayName("setters de configuração são refletidos")
    void settersApply() {
        TenancyProperties properties = new TenancyProperties();
        properties.setHeaderName("X-Escritorio");

        assertEquals("X-Escritorio", properties.getHeaderName());
    }
}
