package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Payload do cancelamento de um compromisso: a justificativa é obrigatória. */
public class AppointmentCancelRequestDTO {

    @NotBlank
    @Schema(example = "Cliente remarcou para a próxima semana")
    private String justification;

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
}
