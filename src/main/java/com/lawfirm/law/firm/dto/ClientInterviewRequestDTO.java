package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Payload de criação/atualização de uma entrevista. */
@Getter
@Setter
public class ClientInterviewRequestDTO {

    /** Data/hora da entrevista. Se omitida na criação, assume o momento do registro. */
    private Instant occurredAt;

    @Positive private Integer durationMinutes;

    /** Conteúdo do componente rich text (HTML/JSON do editor). */
    @NotBlank private String content;

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
