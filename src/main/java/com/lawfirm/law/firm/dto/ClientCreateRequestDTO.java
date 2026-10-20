package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ClientCreateRequestDTO implements ClientWritableFields {
    @NotBlank private String fullName;

    @NotNull private LocalDate birthDate;

    @NotBlank @ValidCPF private String cpf;

    @NotBlank private String motherName;

    @NotBlank private String mobilePhone;

    @NotBlank private String inssPassword;

    @NotNull private Gender gender;

    // optional writable fields
    private String rg;
    @Email private String email;
    private String referencePhone;
    private String referenceResponsible;
    private MaritalStatus maritalStatus;
    @NotNull private BenefitType benefit;
    @NotNull private Situation situation;
    private String beneficiaryNumber;
    private String nitPis;
    private String profession;
    private String ctps;
    private String ctpsSeries;
    private Integer contributionTime;
    private Integer contributionYears;
    private Integer contributionMonths;
    private Integer contributionDays;
    private RequestStage requestStage;
    private String requestStageJustification;
    private String rgIssuer;
    private LocalDate rgIssueDate;
    private String nationality;
    private Boolean isWhatsapp;
    private Boolean hasDisability;
    private String notes;
    private UUID responsibleUserId;
    private ClientType clientType;

    @com.fasterxml.jackson.annotation.JsonProperty("notBillable")
    @com.fasterxml.jackson.annotation.JsonAlias({"notbillable", "not_billable", "not-billable"})
    private Boolean notBillable;

    private UUID createdBy;

    // getters and setters
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public String getInssPassword() {
        return inssPassword;
    }

    public void setInssPassword(String inssPassword) {
        this.inssPassword = inssPassword;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
}
