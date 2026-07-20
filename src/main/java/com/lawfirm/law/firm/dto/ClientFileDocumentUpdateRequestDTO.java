package com.lawfirm.law.firm.dto;

/** Atualização parcial dos metadados de um documento (o arquivo em si não muda). */
public class ClientFileDocumentUpdateRequestDTO {

    private String documentType;
    private String notes;

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
