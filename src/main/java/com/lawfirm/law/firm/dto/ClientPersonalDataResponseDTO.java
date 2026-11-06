package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({"clientId"})
@Getter
@Setter
public class ClientPersonalDataResponseDTO {

    private UUID id;
    private UUID clientId;

    private String fullName;
    private String firstName;
    private String lastName;

    private LocalDate birthDate;

    private Integer age;

    private String cpf;

    private String rg;

    private String rgIssuer;

    private LocalDate rgIssueDate;

    private String motherName;

    private Gender gender;

    private MaritalStatus maritalStatus;

    private String nationality;

    private String mobilePhone;

    private Boolean isWhatsapp;

    private String referencePhone;

    private String referenceResponsible;

    private String email;

    private Boolean hasDisability;

    private String profession;
    private String nitPis;
    private String ctps;
    private String ctpsSeries;

    private UUID updatedBy;

    private Instant updatedAt;

    public void setId(UUID id) {
        this.id = id;
        this.clientId = id;
    }

    public UUID getClientId() {
        return clientId != null ? clientId : id;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
        this.id = clientId;
    }
}
