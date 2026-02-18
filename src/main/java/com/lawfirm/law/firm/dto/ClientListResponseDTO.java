package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.util.UUID;

@JsonPropertyOrder({"id", "fullName", "cpf", "mobilePhone", "benefit", "situation", "benefitNumber", "createdAt", "updatedAt"})
public class ClientListResponseDTO {
    private UUID id;
    private String fullName;
    private String cpf;
    private String mobilePhone;
    private BenefitType benefit;
    private Situation situation;
    private Instant createdAt;
    private String benefitNumber;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }

    public BenefitType getBenefit() { return benefit; }
    public void setBenefit(BenefitType benefit) { this.benefit = benefit; }

    public Situation getSituation() { return situation; }
    public void setSituation(Situation situation) { this.situation = situation; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getBenefitNumber() { return benefitNumber; }
    public void setBenefitNumber(String benefitNumber) { this.benefitNumber = benefitNumber; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
