package com.lawfirm.law.firm.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração da multi-tenancy por schema (app.tenancy.*).
 *
 * <ul>
 *   <li>{@code header-name} — cabeçalho HTTP que o frontend envia para identificar o tenant
 *       (necessário no login, antes de haver JWT).
 * </ul>
 *
 * Não há tenant aqui, nem lista nem padrão: os atendidos vêm do catálogo {@code public.tenants}
 * ({@link ActiveTenantSchemas}), e requisição de escritório sem tenant é recusada pelo {@link
 * TenantResolutionFilter}.
 *
 * <p>O identificador de tenant é o próprio nome do schema (ex.: {@code tenant_tania}). Só schemas
 * ativos no catálogo são aceitos — protege contra injeção via header/claim.
 */
@Component
@ConfigurationProperties(prefix = "app.tenancy")
public class TenancyProperties {

    private String headerName = "X-Tenant-Id";

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
