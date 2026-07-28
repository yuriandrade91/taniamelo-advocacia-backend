package com.lawfirm.law.firm.tenant;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração da multi-tenancy por schema (app.tenancy.*).
 *
 * <ul>
 *   <li>{@code schemas} — todos os schemas de tenant que o Flyway migra e a aplicação atende.
 *   <li>{@code default-schema} — usado quando a requisição não informa tenant (ex.: boot, validação
 *       de schema do Hibernate, jobs de sistema).
 *   <li>{@code header-name} — cabeçalho HTTP que o frontend envia para identificar o tenant
 *       (necessário no login, antes de haver JWT).
 * </ul>
 *
 * O identificador de tenant é o próprio nome do schema (ex.: {@code tenant_tania}). Só valores em
 * {@code schemas} são aceitos — protege contra injeção via header/claim.
 */
@Component
@ConfigurationProperties(prefix = "app.tenancy")
public class TenancyProperties {

    private String defaultSchema = "tenant_tania";
    private List<String> schemas = new ArrayList<>(List.of("tenant_tania"));
    private String headerName = "X-Tenant-Id";

    public boolean isKnown(String schema) {
        return schema != null && schemas.contains(schema);
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<String> schemas) {
        this.schemas = schemas;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
