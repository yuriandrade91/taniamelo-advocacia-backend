package com.lawfirm.law.firm.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.validation.ValidCPF;

import jakarta.validation.constraints.NotBlank;

public class ClientListDTO {
    private UUID id;

    @NotBlank
    private String fullName;

    @NotBlank
    @ValidCPF
    private String cpf;

    private String benefitNumber;
    private BenefitType benefit;
    private Situation situation;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    @JsonProperty(access = Access.READ_ONLY)
    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getBenefitNumber() {
        return benefitNumber;
    }

    public void setBenefitNumber(String benefitNumber) {
        this.benefitNumber = benefitNumber;
    }

    public BenefitType getBenefit() {
        return benefit;
    }

    public void setBenefit(BenefitType benefit) {
        this.benefit = benefit;
    }

    public Situation getSituation() {
        return situation;
    }

    public void setSituation(Situation situation) {
        this.situation = situation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @JsonProperty(access = Access.READ_ONLY)
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @JsonProperty(access = Access.READ_ONLY)
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
