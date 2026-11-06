package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Payload do cancelamento de um compromisso: a justificativa é obrigatória. */
@Getter
@Setter
public class AppointmentCancelRequestDTO {

    @NotBlank
    @Schema(example = "Cliente remarcou para a próxima semana")
    private String justification;
}
