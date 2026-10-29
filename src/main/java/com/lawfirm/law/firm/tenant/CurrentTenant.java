package com.lawfirm.law.firm.tenant;

import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import org.springframework.stereotype.Component;

/**
 * Registro do tenant da requisição corrente (resolvido pelo {@link TenantResolutionFilter}), com o
 * id e o slug públicos. Sem tenant no contexto é erro: não há escritório padrão.
 */
@Component
public class CurrentTenant {

    private final TenantRepository tenantRepository;

    public CurrentTenant(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant get() {
        String schema = TenantContext.get();
        if (schema == null) {
            throw new IllegalStateException("Requisição sem tenant");
        }
        return tenantRepository
                .findBySchemaName(schema)
                .orElseThrow(() -> new IllegalStateException("Tenant não encontrado: " + schema));
    }
}
