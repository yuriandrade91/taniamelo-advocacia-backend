package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O que a pessoa pode mudar nos próprios dados.
 *
 * <p>Nome e e-mail, e só. Papel, situação e nome de usuário ficam de fora de propósito: papel é
 * escalada de privilégio disfarçada de edição de perfil, e o nome de usuário é a credencial com que
 * ela entra — trocá-lo pela tela de perfil derrubaria a própria sessão e as referências de quem
 * administra.
 */
public class MyProfileUpdateRequestDTO {

    @Schema(description = "Nome completo, como aparece na trilha de autoria")
    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Schema(description = "E-mail de acesso; precisa continuar único no escritório")
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
