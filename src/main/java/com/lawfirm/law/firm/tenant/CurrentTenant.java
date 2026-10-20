package com.lawfirm.law.firm.tenant;

import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import org.springframework.stereotype.Component;

/**
 * Registro do tenant da requisição corrente (resolvido pelo {@link TenantResolutionFilter}), com o
 * id e o slug públicos. Sem tenant no contexto, vale o schema default.
 */
@Component
public class CurrentTenant {

    private final TenantRepository tenantRepository;
    private final TenancyProperties tenancyProperties;

    public CurrentTenant(TenantRepository tenantRepository, TenancyProperties tenancyProperties) {
        this.tenantRepository = tenantRepository;
        this.tenancyProperties = tenancyProperties;
    }

    public Tenant get() {
        String current = TenantContext.get();
        String schema = current != null ? current : tenancyProperties.getDefaultSchema();
        return tenantRepository
                .findBySchemaName(schema)
                .orElseThrow(() -> new IllegalStateException("Tenant não encontrado: " + schema));
    }
}
