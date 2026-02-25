package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class ClientCreateRequestDTO {
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

    // optional writable fields
    private String rg;
    @Email
    private String email;
    private String referencePhone;
    private String referenceResponsible;
    private MaritalStatus maritalStatus;
    @NotNull
    private BenefitType benefit;
    @NotNull
    private Situation situation;
    private String beneficiaryNumber;
    private String nitPis;
    private String profession;
    private String ctps;
    private String ctpsSeries;
    private Integer contributionTime;
    private Boolean nonBillable;
    private Integer createdBy;

    // getters and setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

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

    public Integer getContributionTime() { return contributionTime; }
    public void setContributionTime(Integer contributionTime) { this.contributionTime = contributionTime; }

    public Boolean getNonBillable() { return nonBillable; }
    public void setNonBillable(Boolean nonBillable) { this.nonBillable = nonBillable; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
}
