package com.lawfirm.law.firm.dto;

import lombok.Getter;
import lombok.Setter;

/** Atualização parcial dos metadados de um documento (o arquivo em si não muda). */
@Getter
@Setter
public class ClientFileDocumentUpdateRequestDTO {

    private String documentType;
    private String notes;
}
