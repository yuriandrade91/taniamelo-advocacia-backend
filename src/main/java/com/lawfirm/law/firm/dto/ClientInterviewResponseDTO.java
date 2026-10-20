package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({"id"})
@Getter
@Setter
public class ClientInterviewResponseDTO {

    private UUID id;
    private Instant occurredAt;
    private Integer durationMinutes;
    private String content;
    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;
}
