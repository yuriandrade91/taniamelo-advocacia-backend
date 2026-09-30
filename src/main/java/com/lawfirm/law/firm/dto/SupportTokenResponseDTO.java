package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta do login de plataforma do suporte. O {@code token} é o token de PLATAFORMA: serve só
 * para abrir sessões de suporte (POST /api/v1/support/sessions/{tenantId}); sozinho não acessa
 * dado de nenhum escritório.
 */
public record SupportTokenResponseDTO(
        @Schema(description = "Token de plataforma (Bearer)") String token,
        @Schema(description = "Tipo do token", example = "Bearer") String tokenType,
        @Schema(description = "Validade em segundos") long expiresInSeconds,
        @Schema(description = "Nome do agente") String name,
        @Schema(description = "E-mail do agente") String email) {}
