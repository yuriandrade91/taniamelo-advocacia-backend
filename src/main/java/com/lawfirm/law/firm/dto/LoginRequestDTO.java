package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais de login. {@code login} aceita o e-mail OU o username do usuário (a autenticação
 * resolve os dois). O tenant é informado à parte, no cabeçalho {@code X-Tenant-Id}.
 */
public class LoginRequestDTO {

    @NotBlank
    @Schema(description = "E-mail ou username do usuário", example = "dra.tania@taniamelo.adv.br")
    private String login;

    @NotBlank
    @Schema(example = "password")
    private String password;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
