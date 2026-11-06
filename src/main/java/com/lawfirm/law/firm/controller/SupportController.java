package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.SupportLoginRequestDTO;
import com.lawfirm.law.firm.dto.SupportSessionResponseDTO;
import com.lawfirm.law.firm.dto.SupportTokenResponseDTO;
import com.lawfirm.law.firm.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acesso da equipe de SUPORTE da plataforma aos escritórios, sem precisar de um usuário por tenant.
 *
 * <p>Fluxo em dois passos:
 *
 * <ol>
 *   <li><b>POST /login</b> — autentica no control-plane (público) e devolve um <b>token de
 *       plataforma</b> (só serve para abrir sessões; não acessa dado de escritório).
 *   <li><b>POST /sessions/{tenantId}</b> — com o token de plataforma, abre uma <b>sessão de
 *       suporte</b> num escritório e devolve um <b>token curto</b> (impersonation) com acesso total
 *       (ADMIN). Use-o como Bearer nas rotas do escritório.
 * </ol>
 *
 * <p>Toda ação feita com o token de sessão é atribuída ao agente na auditoria ({@code
 * audit_log.acting_support_user_id}), mesmo ele não sendo usuário daquele escritório.
 */
@Tag(name = "Suporte", description = "Acesso multi-tenant da equipe de suporte (impersonation)")
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @Operation(
            summary = "Login de plataforma (suporte)",
            description =
                    "Autentica o agente de suporte e devolve o token de plataforma. Não exige tenant"
                            + " nem Bearer. O token retornado só abre sessões de suporte.")
    @SecurityRequirements // sem segurança: endpoint público (como o /auth/login)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SupportTokenResponseDTO>> login(
            @Valid @RequestBody SupportLoginRequestDTO request, HttpServletRequest httpRequest) {
        SupportTokenResponseDTO body =
                supportService.login(request.email(), request.password(), clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.successObject(body));
    }

    @Operation(
            summary = "Abrir sessão de suporte num escritório",
            description =
                    "Requer o token de PLATAFORMA (Bearer). Emite um token de sessão curto com acesso"
                            + " total ao escritório informado. tenantId aceita o slug ou o UUID"
                            + " público do escritório.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/sessions/{tenantId}")
    public ResponseEntity<ApiResponse<SupportSessionResponseDTO>> openSession(
            @Parameter(description = "Slug ou UUID público do escritório", example = "demo")
                    @PathVariable
                    String tenantId,
            Authentication authentication) {
        UUID supportUserId = (UUID) authentication.getPrincipal();
        SupportSessionResponseDTO body = supportService.openSession(supportUserId, tenantId);
        return ResponseEntity.ok(ApiResponse.successObject(body));
    }

    /** IP do cliente para o throttle: primeiro salto do X-Forwarded-For, senão o remoteAddr. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
