package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.TenantPublicDTO;
import com.lawfirm.law.firm.dto.TenantResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta ao catálogo de escritórios ({@code public.tenants}). */
@Service
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    /** O que é seguro dizer antes do login: nunca o nome do schema físico. */
    @Transactional(readOnly = true)
    public TenantPublicDTO resolve(String slug) {
        Tenant tenant =
                repository.findBySlug(slug).orElseThrow(() -> NotFoundException.of("Tenant", slug));
        return new TenantPublicDTO(
                tenant.getId().toString(), tenant.getSlug(), tenant.getRazaoSocial());
    }

    @Transactional(readOnly = true)
    public TenantResponseDTO current() {
        String schema = TenantContext.get();
        return repository
                .findBySchemaName(schema)
                .map(TenantService::toDTO)
                .orElseThrow(() -> NotFoundException.of("Tenant", schema));
    }

    private static TenantResponseDTO toDTO(Tenant tenant) {
        TenantResponseDTO dto = new TenantResponseDTO();
        dto.setId(tenant.getId());
        dto.setSlug(tenant.getSlug());
        dto.setRazaoSocial(tenant.getRazaoSocial());
        dto.setCnpj(tenant.getCnpj());
        dto.setResponsavel(tenant.getResponsavel());
        dto.setEmail(tenant.getEmail());
        dto.setTelefone(tenant.getTelefone());
        dto.setPlano(tenant.getPlano());
        dto.setStatus(tenant.getStatus());
        dto.setCriadoEm(tenant.getCriadoEm());
        dto.setAtualizadoEm(tenant.getAtualizadoEm());
        return dto;
    }
}
