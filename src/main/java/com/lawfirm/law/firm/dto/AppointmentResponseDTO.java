package com.lawfirm.law.firm.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Representação de um compromisso da agenda. Enums saem como label PT-BR (String). */
@Getter
@Setter
public class AppointmentResponseDTO {

    private UUID id;
    private String title;
    private String type;
    private Instant startAt;
    private Instant endAt;
    private String modality;
    private String location;
    private String meetingUrl;
    private String description;
    private String status;
    private String cancellationReason;
    private UUID clientId;
    private String clientName;
    private UUID pastDateAuthorizedBy;
    private Instant pastDateAuthorizedAt;
    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;

    /**
     * Quando foi excluído. Nulo na listagem normal - só a lixeira ({@code GET
     * /appointments/deleted}) devolve registros com isto preenchido.
     */
    private Instant deletedAt;
}
