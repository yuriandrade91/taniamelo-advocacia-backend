package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.TenantPublicDTO;
import com.lawfirm.law.firm.dto.TenantResponseDTO;
import com.lawfirm.law.firm.service.TenantService;
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
 *       é ação de admin de plataforma e continua sem endpoint (ROADMAP).
 * </ul>
 */
@Tag(name = "Tenant", description = "Resolução pública por slug e catálogo do tenant corrente")
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @Operation(
            summary = "Resolver tenant por slug (público)",
            description =
                    "Traduz um slug (ex.: 'tania') no tenantId (UUID) que o front usa no cabeçalho"
                            + " X-Tenant-Id do login. Não exige autenticação nem tenant.")
    @SecurityRequirements
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<TenantPublicDTO>> resolve(@RequestParam String slug) {
        return ResponseEntity.ok(ApiResponse.successObject(service.resolve(slug)));
    }

    @Operation(
            summary = "Tenant corrente",
            description =
                    "Dados do escritório da sessão atual (razão social, CNPJ, plano, status).")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "tenantHeader")
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<TenantResponseDTO>> current() {
        return ResponseEntity.ok(ApiResponse.successObject(service.current()));
    }
}
