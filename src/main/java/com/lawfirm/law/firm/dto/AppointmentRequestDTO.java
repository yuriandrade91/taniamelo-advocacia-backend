package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload de criação/edição de um compromisso da agenda. {@code startAt} e {@code endAt} são
 * obrigatórios, com {@code endAt} posterior a {@code startAt}. {@code justification} é ignorado na
 * criação e obrigatório na edição (validado no service, pois o mesmo DTO serve os dois fluxos).
 */
public class AppointmentRequestDTO {

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

    private String location;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AppointmentType getType() {
        return type;
    }

    public void setType(AppointmentType type) {
        this.type = type;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public AppointmentModality getModality() {
        return modality;
    }

    public void setModality(AppointmentModality modality) {
        this.modality = modality;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public void setMeetingUrl(String meetingUrl) {
        this.meetingUrl = meetingUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public boolean isPastDateAcknowledged() {
        return pastDateAcknowledged;
    }

    public void setPastDateAcknowledged(boolean pastDateAcknowledged) {
        this.pastDateAcknowledged = pastDateAcknowledged;
    }
}
