package com.lawfirm.law.firm.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração da multi-tenancy por schema (app.tenancy.*).
 *
 * <ul>
 *   <li>{@code default-schema} — usado quando a requisição não informa tenant (ex.: boot, validação
 *       de schema do Hibernate, jobs de sistema).
 *   <li>{@code header-name} — cabeçalho HTTP que o frontend envia para identificar o tenant
 *       (necessário no login, antes de haver JWT).
 * </ul>
 *
 * Não há lista de tenants aqui: os atendidos vêm do catálogo {@code public.tenants} ({@link
 * ActiveTenantSchemas}), então cadastrar um escritório não exige mexer em configuração.
 *
 * <p>O identificador de tenant é o próprio nome do schema (ex.: {@code tenant_tania}). Só schemas
 * ativos no catálogo são aceitos — protege contra injeção via header/claim.
 */
@Component
@ConfigurationProperties(prefix = "app.tenancy")
public class TenancyProperties {

    private String defaultSchema = "tenant_tania";
    private String headerName = "X-Tenant-Id";

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
