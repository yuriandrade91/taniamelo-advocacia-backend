package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantContext: schema do tenant corrente numa ThreadLocal")
class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("sem set, get devolve null")
    void emptyByDefault() {
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("set e get na mesma thread")
    void setThenGet() {
        TenantContext.set("tenant_tania");
        assertEquals("tenant_tania", TenantContext.get());
    }

    @Test
    @DisplayName(
            "clear remove o valor - essencial para não vazar entre requisições da mesma thread")
    void clearRemovesValue() {
        TenantContext.set("tenant_demo");
        TenantContext.clear();
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("o valor não vaza para outra thread")
    void isolatedPerThread() throws Exception {
        TenantContext.set("tenant_tania");

        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> other = executor.submit(TenantContext::get);
            assertNull(other.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("tenant_tania", TenantContext.get());
    }

    @Test
    @DisplayName("set sobrescreve o valor anterior")
    void setOverwrites() {
        TenantContext.set("tenant_a");
        TenantContext.set("tenant_b");
        assertEquals("tenant_b", TenantContext.get());
    }

    @Test
    @DisplayName("set(null) é tratado como ausência de tenant")
    void setNull() {
        TenantContext.set("tenant_a");
        TenantContext.set(null);
        assertNull(TenantContext.get());
    }
}
