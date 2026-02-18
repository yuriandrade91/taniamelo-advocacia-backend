package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonPropertyOrder({"id"})
public class ClientDetailsDTO {
    private UUID id;

    // ============================================
    // OBRIGATÓRIOS
    // ============================================
    @NotBlank
    private String fullName;

    @NotNull
    private LocalDate birthDate;

    @NotBlank
    @ValidCPF
    private String cpf;

    @NotBlank
    private String motherName;

    @NotBlank
    private String mobilePhone;

    @NotBlank
    private String inssPassword;

    @NotNull
    private Gender gender;

    // ============================================
    // OPCIONAIS
    // ============================================
    private String firstName;
    private String lastName;

    private String rg;

    @Email
    private String email;

    private String referencePhone;
    private String referenceResponsible;

    private MaritalStatus maritalStatus; // enum

    private BenefitType benefit;
    private Situation situation;
    private String benefitNumber;

    private String nitPis;
    private String profession;

    private String ctps;
    private String ctpsSeries;
    private Integer contributionTime;

    private Instant createdAt;
    private Instant updatedAt;
    private Boolean nonBillable;
    private Integer createdBy;

    private Integer age;

    public UUID getId() { return id; }
    @JsonProperty(access = Access.READ_ONLY)
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public MaritalStatus getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(MaritalStatus maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getRg() { return rg; }
    public void setRg(String rg) { this.rg = rg; }

    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }

    public String getReferencePhone() { return referencePhone; }
    public void setReferencePhone(String referencePhone) { this.referencePhone = referencePhone; }

    public String getReferenceResponsible() { return referenceResponsible; }
    public void setReferenceResponsible(String referenceResponsible) { this.referenceResponsible = referenceResponsible; }

    public BenefitType getBenefit() { return benefit; }
    public void setBenefit(BenefitType benefit) { this.benefit = benefit; }

    public Situation getSituation() { return situation; }
    public void setSituation(Situation situation) { this.situation = situation; }

    public String getBenefitNumber() { return benefitNumber; }
    public void setBenefitNumber(String benefitNumber) { this.benefitNumber = benefitNumber; }

    public String getNitPis() { return nitPis; }
    public void setNitPis(String nitPis) { this.nitPis = nitPis; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getCtps() { return ctps; }
    public void setCtps(String ctps) { this.ctps = ctps; }

    public String getCtpsSeries() { return ctpsSeries; }
    public void setCtpsSeries(String ctpsSeries) { this.ctpsSeries = ctpsSeries; }

    public String getInssPassword() { return inssPassword; }
    public void setInssPassword(String inssPassword) { this.inssPassword = inssPassword; }

    public Integer getContributionTime() { return contributionTime; }
    public void setContributionTime(Integer contributionTime) { this.contributionTime = contributionTime; }

    public Instant getCreatedAt() { return createdAt; }
    @JsonProperty(access = Access.READ_ONLY)
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    @JsonProperty(access = Access.READ_ONLY)
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getNonBillable() { return nonBillable; }
    public void setNonBillable(Boolean nonBillable) { this.nonBillable = nonBillable; }

    public Integer getCreatedBy() { return createdBy; }
    @JsonProperty(access = Access.READ_ONLY)
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    @JsonProperty(access = Access.READ_ONLY)
    public void setAge(Integer age) { this.age = age; }
}
