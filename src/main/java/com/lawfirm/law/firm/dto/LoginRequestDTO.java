package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Credenciais de login. {@code login} aceita o e-mail OU o username do usuário (a autenticação
 * resolve os dois). O tenant é informado à parte, no cabeçalho {@code X-Tenant-Id}.
 */
@Getter
@Setter
public class LoginRequestDTO {

    @NotBlank
    @Schema(description = "E-mail ou username do usuário", example = "dra.tania@taniamelo.adv.br")
    private String login;

    @NotBlank
    @Schema(example = "password")
    private String password;
}
