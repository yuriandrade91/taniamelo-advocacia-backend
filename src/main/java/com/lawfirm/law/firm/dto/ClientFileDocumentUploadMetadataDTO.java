package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotBlank;

/** Metadados de um documento no upload em lote (uma entrada por arquivo, na mesma ordem). */
public class ClientFileDocumentUploadMetadataDTO {

    @NotBlank
    private String documentType;

    private String notes;

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
