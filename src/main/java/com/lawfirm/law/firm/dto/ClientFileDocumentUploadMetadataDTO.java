package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Metadados de um documento no upload em lote (uma entrada por arquivo, na mesma ordem). */
@Getter
@Setter
public class ClientFileDocumentUploadMetadataDTO {

    @NotBlank private String documentType;

    private String notes;

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }
}
