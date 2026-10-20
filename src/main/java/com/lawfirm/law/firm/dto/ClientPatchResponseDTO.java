package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class ClientPatchResponseDTO {
    private String message;

    public ClientPatchResponseDTO() {}

    public ClientPatchResponseDTO(String message) {
        this.message = message;
    }
}
