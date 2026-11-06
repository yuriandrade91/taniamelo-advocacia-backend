package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({"id"})
@Getter
@Setter
public class ClientFileDocumentResponseDTO {

    private UUID id;
    private String documentType;
    private String originalFilename;
    private String mimeType;
    private Long fileSizeBytes;
    private String notes;
    private UUID uploadedBy;
    private Instant uploadedAt;
    private UUID updatedBy;
    private Instant updatedAt;
    private String downloadUrl;
}
