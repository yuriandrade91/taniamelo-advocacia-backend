package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Corpo de POST/PUT de um endereço. Usado tanto para criar quanto para substituir por completo. */
@Getter
@Setter
public class ClientAddressRequestDTO {

    private String addressType;

    @Size(max = 255)
    @NotBlank
    private String street;

    @Size(max = 20)
    private String addressNumber;

    @Size(max = 100)
    private String complement;

    @Size(max = 100)
    private String neighborhood;

    @Size(max = 100)
    @NotBlank
    private String city;

    @Size(max = 2)
    @NotBlank
    private String state;

    @Size(max = 10)
    private String zipCode;

    /** Se true, esta passa a ser a principal (a anterior é desmarcada automaticamente). */
    private Boolean isPrimary;
}
