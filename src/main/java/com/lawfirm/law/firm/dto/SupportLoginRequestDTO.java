package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Credenciais do agente de suporte (control-plane). Autentica em public.support_users. */
public record SupportLoginRequestDTO(
        @Schema(description = "E-mail do agente de suporte", example = "suporte@plataforma.adv.br")
                @NotBlank
                @Email
                String email,
        @Schema(description = "Senha", example = "trocar-esta-senha") @NotBlank String password) {}
