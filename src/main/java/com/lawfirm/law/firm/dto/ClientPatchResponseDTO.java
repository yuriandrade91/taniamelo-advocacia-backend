package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientPatchResponseDTO {
    private String message;

    public ClientPatchResponseDTO() {}

    public ClientPatchResponseDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
