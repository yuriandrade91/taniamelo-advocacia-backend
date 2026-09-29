package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Troca da própria senha.
 *
 * <p>A senha atual é obrigatória mesmo com a sessão já autenticada. O token dura horas e a máquina
 * fica destravada: sem a confirmação, quem passar por um computador aberto troca a senha e toma a
 * conta. Pedir a atual custa um campo e fecha isso.
 *
 * <p>O mínimo de 8 caracteres é o mesmo do cadastro. Não há regra de maiúscula e símbolo: exigir
 * composição produz senha curta e previsível anotada no monitor, e o comprimento é o que de fato
 * pesa.
 */
public class ChangePasswordRequestDTO {

    @Schema(description = "Senha atual, para confirmar que é a pessoa")
    @NotBlank
    private String currentPassword;

    @Schema(description = "Nova senha, de 8 caracteres para cima")
    @NotBlank
    @Size(min = 8, max = 100, message = "A nova senha precisa ter ao menos 8 caracteres.")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
