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

    public String getRg() {
        return rg;
    }

    public void setRg(String rg) {
        this.rg = rg;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getReferencePhone() {
        return referencePhone;
    }

    public void setReferencePhone(String referencePhone) {
        this.referencePhone = referencePhone;
    }

    public String getReferenceResponsible() {
        return referenceResponsible;
    }

    public void setReferenceResponsible(String referenceResponsible) {
        this.referenceResponsible = referenceResponsible;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
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

    public String getBeneficiaryNumber() {
        return beneficiaryNumber;
    }

    public void setBeneficiaryNumber(String beneficiaryNumber) {
        this.beneficiaryNumber = beneficiaryNumber;
    }

    public String getNitPis() {
        return nitPis;
    }

    public void setNitPis(String nitPis) {
        this.nitPis = nitPis;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getCtps() {
        return ctps;
    }

    public void setCtps(String ctps) {
        this.ctps = ctps;
    }

    public String getCtpsSeries() {
        return ctpsSeries;
    }

    public void setCtpsSeries(String ctpsSeries) {
        this.ctpsSeries = ctpsSeries;
    }

    public Integer getContributionTime() {
        return contributionTime;
    }

    public void setContributionTime(Integer contributionTime) {
        this.contributionTime = contributionTime;
    }

    @Override
    public RequestStage getRequestStage() {
        return requestStage;
    }

    public void setRequestStage(RequestStage requestStage) {
        this.requestStage = requestStage;
    }

    @Override
    public Integer getContributionYears() {
        return contributionYears;
    }

    public void setContributionYears(Integer contributionYears) {
        this.contributionYears = contributionYears;
    }

    @Override
    public Integer getContributionMonths() {
        return contributionMonths;
    }

    public void setContributionMonths(Integer contributionMonths) {
        this.contributionMonths = contributionMonths;
    }

    @Override
    public Integer getContributionDays() {
        return contributionDays;
    }

    public void setContributionDays(Integer contributionDays) {
        this.contributionDays = contributionDays;
    }

    @Override
    public Boolean getNotBillable() {
        return notBillable;
    }

    public void setNotBillable(Boolean notBillable) {
        this.notBillable = notBillable;
    }

    @Override
    public String getRgIssuer() {
        return rgIssuer;
    }

    public void setRgIssuer(String rgIssuer) {
        this.rgIssuer = rgIssuer;
    }

    @Override
    public LocalDate getRgIssueDate() {
        return rgIssueDate;
    }

    public void setRgIssueDate(LocalDate rgIssueDate) {
        this.rgIssueDate = rgIssueDate;
    }

    @Override
    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    @Override
    public Boolean getIsWhatsapp() {
        return isWhatsapp;
    }

    public void setIsWhatsapp(Boolean isWhatsapp) {
        this.isWhatsapp = isWhatsapp;
    }

    @Override
    public Boolean getHasDisability() {
        return hasDisability;
    }

    public void setHasDisability(Boolean hasDisability) {
        this.hasDisability = hasDisability;
    }

    @Override
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public UUID getResponsibleUserId() {
        return responsibleUserId;
    }

    public void setResponsibleUserId(UUID responsibleUserId) {
        this.responsibleUserId = responsibleUserId;
    }

    @Override
    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public String getRequestStageJustification() {
        return requestStageJustification;
    }

    public void setRequestStageJustification(String requestStageJustification) {
        this.requestStageJustification = requestStageJustification;
    }
}
