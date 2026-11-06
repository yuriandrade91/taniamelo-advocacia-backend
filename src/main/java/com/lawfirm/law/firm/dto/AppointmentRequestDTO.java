package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de criação/edição de um compromisso da agenda. {@code startAt} e {@code endAt} são
 * obrigatórios, com {@code endAt} posterior a {@code startAt}. {@code justification} é ignorado na
 * criação e obrigatório na edição (validado no service, pois o mesmo DTO serve os dois fluxos).
 */
@Getter
@Setter
public class AppointmentRequestDTO {

    @Size(max = 255)
    @NotBlank
    @Schema(example = "Entrevista com Yuri Andrade")
    private String title;

    @NotNull private AppointmentType type;

    @NotNull
    @Schema(example = "2026-08-20T14:30:00Z")
    private Instant startAt;

    @NotNull
    @Schema(example = "2026-08-20T15:30:00Z")
    private Instant endAt;

    private AppointmentModality modality;

    @Size(max = 255)
    private String location;

    @Size(max = 500)
    @Schema(example = "https://meet.google.com/xxx-yyyy-zzz")
    private String meetingUrl;

    private String description;

    @Schema(description = "Vínculo opcional a um cliente cadastrado (validado se informado)")
    private UUID clientId;

    @Schema(
            description =
                    "Nome livre da pessoa quando NÃO há cliente cadastrado. Ignorado se clientId"
                            + " for informado (aí o nome vem do cliente).",
            example = "Yuri Andrade")
    @Size(max = 255)
    private String clientName;

    @Schema(description = "Obrigatória na edição (justificativa da alteração)")
    private String justification;

    @Schema(
            description =
                    "Confirmação de ciência quando a data (start) está no passado. Sem isso, um"
                            + " compromisso com data passada é rejeitado (422 PAST_DATE_NOT_CONFIRMED);"
                            + " com true, é criado e a ciência fica registrada para auditoria.",
            defaultValue = "false")
    private boolean pastDateAcknowledged;

    @JsonIgnore
    @AssertTrue(message = "A data/hora de término deve ser posterior à de início")
    public boolean isEndAfterStart() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    public AppointmentType getType() {
        return type;
    }

    public void setType(AppointmentType type) {
        this.type = type;
    }
}
