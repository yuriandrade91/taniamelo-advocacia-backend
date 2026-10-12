package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.model.converter.CryptoConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "clients")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
public class Client {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    // ============================================
    // OBRIGATÓRIOS
    // ============================================
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "birth_mmdd", insertable = false, updatable = false)
    private Short birthMmdd;

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
    @Convert(converter = CryptoConverter.class)
    @Column(name = "inss_password", nullable = false, length = 255)
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

    @Column(name = "request_stage", length = 100)
    private RequestStage requestStage;

    // ============================================
    // OPCIONAIS - GERADOS AUTOMATICAMENTE
    // ============================================
    @Transient private String firstName;

    @Transient private String lastName;

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
    private MaritalStatus
            maritalStatus; // Solteiro(a), Casado(a), Separada(a), Divorciado(a), Viúvo(a)

    @Column(name = "benefit_number", unique = true, length = 30)
    private String beneficiaryNumber;

    @Column(name = "nit_pis", unique = true, length = 20)
    private String nitPis;

    @Column(name = "profession")
    private String profession;

    @Column(name = "ctps", unique = true, length = 30)
    private String ctps;

    @Column(name = "ctps_series", unique = true, length = 20)
    private String ctpsSeries;

    @Column(name = "contribution_years")
    private Integer contributionYears;

    @Column(name = "contribution_months")
    private Integer contributionMonths;

    @Column(name = "contribution_days")
    private Integer contributionDays;

    @Column(name = "contribution_in_months")
    private Integer contributionInMonths;

    // ============================================
    // ENDEREÇO
    // ============================================
    // Endereço é persistido em ClientAddress; estes campos legados não mapeiam colunas em clients.
    @Transient private String street;

    @Transient private String addressNumber;

    @Transient private String complement;

    @Transient private String neighborhood;

    @Transient private String city;

    @Transient private String state;

    @Transient private String zipCode;

    // ============================================
    // DADOS COMPLEMENTARES DE CADASTRO
    // ============================================
    @Column(name = "rg_issuer", length = 20)
    private String rgIssuer;

    @Column(name = "rg_issue_date")
    private LocalDate rgIssueDate;

    @Column(name = "nationality", length = 50)
    private String nationality = "Brasileira";

    @Column(name = "is_whatsapp", nullable = false)
    private Boolean isWhatsapp = true;

    @Column(name = "has_disability", nullable = false)
    private Boolean hasDisability = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Column(name = "client_type", nullable = false, length = 20)
    private ClientType clientType = ClientType.POTENCIAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "not_billable", nullable = false)
    private Boolean notBillable = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Constructors
    public Client() {
        // createdAt will be set by DB default NOW() on insert; keep null on new entity so DB
        // default applies
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
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getFirstName() {
        String[] nameParts = nameParts();
        return nameParts.length == 0 ? null : nameParts[0];
    }

    public String getLastName() {
        String[] nameParts = nameParts();
        return nameParts.length < 2 ? null : nameParts[1];
    }

    private String[] nameParts() {
        return fullName == null || fullName.isBlank()
                ? new String[0]
                : fullName.trim().split("\\s+", 2);
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getRg() {
        return rg;
    }

    public void setRg(String rg) {
        this.rg = rg;
    }

    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
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

    public RequestStage getRequestStage() {
        return requestStage;
    }

    public void setRequestStage(RequestStage requestStage) {
        this.requestStage = requestStage;
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

    public String getInssPassword() {
        return inssPassword;
    }

    public void setInssPassword(String inssPassword) {
        this.inssPassword = inssPassword;
    }

    public Integer getContributionYears() {
        return contributionYears;
    }

    public void setContributionYears(Integer contributionYears) {
        this.contributionYears = contributionYears;
    }

    public Integer getContributionMonths() {
        return contributionMonths;
    }

    public void setContributionMonths(Integer contributionMonths) {
        this.contributionMonths = contributionMonths;
    }

    public Integer getContributionDays() {
        return contributionDays;
    }

    public void setContributionDays(Integer contributionDays) {
        this.contributionDays = contributionDays;
    }

    public Integer getContributionInMonths() {
        return contributionInMonths;
    }

    public void setContributionInMonths(Integer contributionInMonths) {
        this.contributionInMonths = contributionInMonths;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getAddressNumber() {
        return addressNumber;
    }

    public void setAddressNumber(String addressNumber) {
        this.addressNumber = addressNumber;
    }

    public String getComplement() {
        return complement;
    }

    public void setComplement(String complement) {
        this.complement = complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getRgIssuer() {
        return rgIssuer;
    }

    public void setRgIssuer(String rgIssuer) {
        this.rgIssuer = rgIssuer;
    }

    public LocalDate getRgIssueDate() {
        return rgIssueDate;
    }

    public void setRgIssueDate(LocalDate rgIssueDate) {
        this.rgIssueDate = rgIssueDate;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public Boolean getIsWhatsapp() {
        return isWhatsapp;
    }

    public void setIsWhatsapp(Boolean isWhatsapp) {
        this.isWhatsapp = isWhatsapp;
    }

    public Boolean getHasDisability() {
        return hasDisability;
    }

    public void setHasDisability(Boolean hasDisability) {
        this.hasDisability = hasDisability;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getResponsibleUserId() {
        return responsibleUserId;
    }

    public void setResponsibleUserId(UUID responsibleUserId) {
        this.responsibleUserId = responsibleUserId;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        this.clientType = clientType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getNotBillable() {
        return notBillable;
    }

    public void setNotBillable(Boolean notBillable) {
        this.notBillable = notBillable;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }
}
