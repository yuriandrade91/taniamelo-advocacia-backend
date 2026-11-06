package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta ao abrir uma sessão de suporte num tenant. O {@code token} é o token de SESSÃO
 * (impersonation): use-o como Bearer nas rotas do escritório. É curto e dá acesso total (ADMIN),
 * com toda ação atribuída ao agente na auditoria.
 */
public record SupportSessionResponseDTO(
        @Schema(description = "Token de sessão / impersonation (Bearer)") String token,
        @Schema(description = "Tipo do token", example = "Bearer") String tokenType,
        @Schema(description = "Validade em segundos") long expiresInSeconds,
        @Schema(description = "UUID público do escritório") String tenantId,
        @Schema(description = "Slug do escritório") String tenantSlug,
        @Schema(description = "Razão social do escritório") String tenantRazaoSocial) {}
