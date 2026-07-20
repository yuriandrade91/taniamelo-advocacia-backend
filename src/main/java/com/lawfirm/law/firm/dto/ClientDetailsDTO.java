package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Resposta completa de um cliente (somente saída). Endereços, arquivos,
 * entrevistas e pagamentos são recursos próprios sob /clients/{id}/...
 */
@JsonPropertyOrder({"id"})
public class ClientDetailsDTO {

    private UUID id;

    private String fullName;

    private LocalDate birthDate;

    private Integer age;

    private String cpf;

    private String motherName;

    private String mobilePhone;

    private String inssPassword;

    private Gender gender;

    private String rg;

    private String rgIssuer;

    private LocalDate rgIssueDate;

    private String email;

    private String referencePhone;

    private String referenceResponsible;

    private MaritalStatus maritalStatus;

    private BenefitType benefit;

    private Situation situation;

    private String beneficiaryNumber;

    private String nitPis;

    private String profession;

    private String ctps;

    private String ctpsSeries;

    private String contributionTime;

    private Integer contributionInMonths;

    private String nationality;

    private Boolean isWhatsapp;

    private Boolean hasDisability;

    private String notes;

    private UUID responsibleUserId;

    private ClientType clientType;

    private Boolean notBillable;

    private Instant createdAt;

    private Instant updatedAt;

    private UUID createdBy;

    private UUID updatedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }

    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }

    public String getInssPassword() { return inssPassword; }
    public void setInssPassword(String inssPassword) { this.inssPassword = inssPassword; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getRg() { return rg; }
    public void setRg(String rg) { this.rg = rg; }

    public String getRgIssuer() { return rgIssuer; }
    public void setRgIssuer(String rgIssuer) { this.rgIssuer = rgIssuer; }

    public LocalDate getRgIssueDate() { return rgIssueDate; }
    public void setRgIssueDate(LocalDate rgIssueDate) { this.rgIssueDate = rgIssueDate; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getReferencePhone() { return referencePhone; }
    public void setReferencePhone(String referencePhone) { this.referencePhone = referencePhone; }

    public String getReferenceResponsible() { return referenceResponsible; }
    public void setReferenceResponsible(String referenceResponsible) { this.referenceResponsible = referenceResponsible; }

    public MaritalStatus getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(MaritalStatus maritalStatus) { this.maritalStatus = maritalStatus; }

    public BenefitType getBenefit() { return benefit; }
    public void setBenefit(BenefitType benefit) { this.benefit = benefit; }

    public Situation getSituation() { return situation; }
    public void setSituation(Situation situation) { this.situation = situation; }

    public String getBeneficiaryNumber() { return beneficiaryNumber; }
    public void setBeneficiaryNumber(String beneficiaryNumber) { this.beneficiaryNumber = beneficiaryNumber; }

    public String getNitPis() { return nitPis; }
    public void setNitPis(String nitPis) { this.nitPis = nitPis; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getCtps() { return ctps; }
    public void setCtps(String ctps) { this.ctps = ctps; }

    public String getCtpsSeries() { return ctpsSeries; }
    public void setCtpsSeries(String ctpsSeries) { this.ctpsSeries = ctpsSeries; }

    public String getContributionTime() { return contributionTime; }
    public void setContributionTime(String contributionTime) { this.contributionTime = contributionTime; }

    public Integer getContributionInMonths() { return contributionInMonths; }
    public void setContributionInMonths(Integer contributionInMonths) { this.contributionInMonths = contributionInMonths; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public Boolean getIsWhatsapp() { return isWhatsapp; }
    public void setIsWhatsapp(Boolean isWhatsapp) { this.isWhatsapp = isWhatsapp; }

    public Boolean getHasDisability() { return hasDisability; }
    public void setHasDisability(Boolean hasDisability) { this.hasDisability = hasDisability; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getResponsibleUserId() { return responsibleUserId; }
    public void setResponsibleUserId(UUID responsibleUserId) { this.responsibleUserId = responsibleUserId; }

    public ClientType getClientType() { return clientType; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }

    public Boolean getNotBillable() { return notBillable; }
    public void setNotBillable(Boolean notBillable) { this.notBillable = notBillable; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

}
