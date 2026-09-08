package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.TenantPublicDTO;
import com.lawfirm.law.firm.dto.TenantResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import com.lawfirm.law.firm.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de tenants (control-plane).
 *
 * <ul>
 *   <li>{@code GET /resolve?slug=...} — PÚBLICO: o front descobre o {@code tenantId} (UUID opaco) a
 *       partir do slug do subdomínio, antes do login, para enviá-lo no cabeçalho X-Tenant-Id.
 *   <li>{@code GET /current} — autenticado: dados do escritório da sessão. Listar TODOS os tenants
 *       é ação de admin de plataforma e fica para quando houver autorização por papel (ROADMAP).
 * </ul>
 */
@Tag(name = "Tenant", description = "Resolução pública por slug e catálogo do tenant corrente")
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantRepository tenantRepository;
    private final TenancyProperties tenancyProperties;

    public TenantController(
            TenantRepository tenantRepository, TenancyProperties tenancyProperties) {
        this.tenantRepository = tenantRepository;
        this.tenancyProperties = tenancyProperties;
    }

    @Operation(
            summary = "Resolver tenant por slug (público)",
            description =
                    "Traduz um slug (ex.: 'tania') no tenantId (UUID) que o front usa no cabeçalho"
                            + " X-Tenant-Id do login. Não exige autenticação nem tenant.")
    @SecurityRequirements
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<TenantPublicDTO>> resolve(@RequestParam String slug) {
        Tenant tenant =
                tenantRepository
                        .findBySlug(slug)
                        .orElseThrow(() -> NotFoundException.of("Tenant", slug));
        TenantPublicDTO dto =
                new TenantPublicDTO(
                        tenant.getId().toString(), tenant.getSlug(), tenant.getRazaoSocial());
        return ResponseEntity.ok(ApiResponse.successObject(dto));
    }

    @Operation(
            summary = "Tenant corrente",
            description =
                    "Dados do escritório da sessão atual (razão social, CNPJ, plano, status).")
    @SecurityRequirements({
        @SecurityRequirement(name = "bearerAuth"),
        @SecurityRequirement(name = "tenantHeader")
    })
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<TenantResponseDTO>> current() {
        String schema = currentSchema();
        Tenant tenant =
                tenantRepository
                        .findBySchemaName(schema)
                        .orElseThrow(() -> NotFoundException.of("Tenant", schema));
        return ResponseEntity.ok(ApiResponse.successObject(toDTO(tenant)));
    }

    private String currentSchema() {
        String current = TenantContext.get();
        return current != null ? current : tenancyProperties.getDefaultSchema();
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
