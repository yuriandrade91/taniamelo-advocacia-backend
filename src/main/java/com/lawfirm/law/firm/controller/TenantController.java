package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.TenantPublicDTO;
import com.lawfirm.law.firm.dto.TenantResponseDTO;
import com.lawfirm.law.firm.security.RequerAdmin;
import com.lawfirm.law.firm.service.TenantLogo;
import com.lawfirm.law.firm.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Catálogo de tenants (control-plane).
 *
 * <ul>
 *   <li>{@code GET /resolve?slug=...} — PÚBLICO: o front descobre o {@code tenantId} (UUID opaco) a
 *       partir do slug do subdomínio, antes do login, para enviá-lo no cabeçalho X-Tenant-Id.
 *   <li>{@code GET /logo?slug=...} — PÚBLICO: a logo que o escritório enviou, que o login mostra
 *       antes de haver sessão e que um {@code <img>} pede sem cabeçalho nenhum.
 *   <li>{@code GET /current} — autenticado: dados do escritório da sessão. Listar TODOS os tenants
 *       é ação de admin de plataforma e continua sem endpoint (ROADMAP).
 *   <li>{@code PUT|DELETE /current/logo} — ADMIN: troca a logo ou volta à marca padrão.
 * </ul>
 */
@Tag(name = "Tenant", description = "Resolução pública por slug e catálogo do tenant corrente")
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    /** A URL leva a versão: pedida com ela, a imagem não muda nunca mais. */
    private static final CacheControl VERSIONED =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @Operation(
            summary = "Resolver tenant por slug (público)",
            description =
                    "Traduz um slug (ex.: 'tania') no tenantId (UUID) que o front usa no cabeçalho"
                            + " X-Tenant-Id do login. Não exige autenticação nem tenant. logoUrl é"
                            + " nulo enquanto o escritório usa a marca padrão.")
    @SecurityRequirements
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<TenantPublicDTO>> resolve(@RequestParam String slug) {
        return ResponseEntity.ok(ApiResponse.successObject(service.resolve(slug)));
    }

    @Operation(
            summary = "Logo do escritório (público)",
            description =
                    "A imagem que o escritório enviou. 404 enquanto ele usa a marca padrão: o front"
                            + " só pede esta rota pelo logoUrl do resolve ou do current, que já"
                            + " traz a versão (v) e deixa a imagem em cache.")
    @SecurityRequirements
    @GetMapping("/logo")
    public ResponseEntity<Resource> logo(
            @RequestParam String slug, @RequestParam(required = false) String v) {
        TenantLogo logo = service.logo(slug);
        return ResponseEntity.ok()
                .cacheControl(logo.isVersion(v) ? VERSIONED : CacheControl.noCache())
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(logo.resource());
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

    @Operation(
            summary = "Enviar a logo do escritório",
            description =
                    "PNG, JPEG ou WebP de até 2 MB, na parte 'file'. O tipo é conferido nos bytes;"
                            + " SVG não é aceito. Substitui a logo anterior em todas as telas e"
                            + " devolve o escritório com o novo logoUrl.")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "tenantHeader")
    @RequerAdmin
    @PutMapping(path = "/current/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TenantResponseDTO>> uploadLogo(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.successObject(service.uploadLogo(file)));
    }

    @Operation(
            summary = "Voltar à marca padrão",
            description = "Remove a logo enviada; as telas voltam a mostrar a marca do sistema.")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "tenantHeader")
    @RequerAdmin
    @DeleteMapping("/current/logo")
    public ResponseEntity<Void> removeLogo() {
        service.removeLogo();
        return ResponseEntity.noContent().build();
    }
}
