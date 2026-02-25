package com.lawfirm.law.firm.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    // ============================================
    // OBRIGATÓRIOS
    // ============================================
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @NotBlank
    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    private String cpf;

    @NotBlank
    @Column(name = "mother_name", nullable = false)
    private String motherName;

    @NotBlank
    @Column(name = "mobile_phone", nullable = false, unique = true, length = 20)
    private String mobilePhone;

    @NotBlank
    @Column(name = "inss_password", nullable = false)
    private String inssPassword;

    @NotNull
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @NotNull
    @Column(name = "situation", length = 100)
    private Situation situation;

    @NotNull
    @Column(name = "benefit", length = 100)
    private BenefitType benefit;

    // ============================================
    // OPCIONAIS - GERADOS AUTOMATICAMENTE
    // ============================================
    @Column(name = "first_name", insertable = false, updatable = false)
    private String firstName;

    @Column(name = "last_name", insertable = false, updatable = false)
    private String lastName;

    // ============================================
    // OPCIONAIS
    // ============================================
    @Column(name = "rg", unique = true, length = 20)
    private String rg;

    @Email
    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "reference_phone", unique = true, length = 20)
    private String referencePhone;

    @Column(name = "reference_responsible")
    private String referenceResponsible;

    @Column(name = "marital_status", length = 50)
    private MaritalStatus maritalStatus; // Solteiro(a), Casado(a), Separada(a), Divorciado(a), Viúvo(a)
    
    @Column(name = "beneficiary_number", unique = true, length = 30)
    private String beneficiaryNumber;

    @Column(name = "nit_pis", unique = true, length = 20)
    private String nitPis;

    @Column(name = "profession")
    private String profession;

    @Column(name = "ctps", unique = true, length = 30)
    private String ctps;

    @Column(name = "ctps_series", unique = true, length = 20)
    private String ctpsSeries;

    @Column(name = "contribution_time")
    private Integer contributionTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "non_billable", nullable = false)
    private Boolean nonBillable = false;

    @Column(name = "created_by")
    private Integer createdBy;

    // Constructors
    public Client() {
        // createdAt will be set by DB default NOW() on insert; keep null on new entity so DB default applies
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getFirstName() { return firstName; }

    public String getLastName() { return lastName; }

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

    public String getInssPassword() { return inssPassword; }
    public void setInssPassword(String inssPassword) { this.inssPassword = inssPassword; }

    public Integer getContributionTime() { return contributionTime; }
    public void setContributionTime(Integer contributionTime) { this.contributionTime = contributionTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getNonBillable() { return nonBillable; }
    public void setNonBillable(Boolean nonBillable) { this.nonBillable = nonBillable; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
}
