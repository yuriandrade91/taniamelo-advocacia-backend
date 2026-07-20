package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.model.converter.CryptoConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cliente do escritório. Decisões de modelagem:
 *  - Endereços NÃO ficam embutidos aqui: são a coleção 1:N client_addresses
 *    (um marcado como principal). O endereço embutido antigo foi removido.
 *  - first_name/last_name gerados no banco foram removidos: derivados de
 *    fullName quando necessário, sem duplicar estado.
 *  - Únicos no banco: cpf, nit_pis e benefit_number (identificadores reais).
 *    Telefones, e-mail, RG e CTPS não são únicos - podem se repetir
 *    legitimamente entre clientes (casal com mesmo e-mail/telefone, RG de
 *    estados diferentes etc.).
 *  - inss_password é criptografada em repouso via {@link CryptoConverter}.
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ── Obrigatórios ──

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @NotNull
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @NotBlank
    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    private String cpf;

    @NotBlank
    @Column(name = "mother_name", nullable = false)
    private String motherName;

    @NotBlank
    @Column(name = "mobile_phone", nullable = false, length = 20)
    private String mobilePhone;

    @NotBlank
    @Convert(converter = CryptoConverter.class)
    @Column(name = "inss_password", nullable = false, length = 255)
    private String inssPassword;

    @NotNull
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @NotNull
    @Column(name = "situation", nullable = false, length = 100)
    private Situation situation;

    @NotNull
    @Column(name = "benefit", nullable = false, length = 100)
    private BenefitType benefit;

    // ── Identificação complementar ──

    @Column(name = "rg", length = 20)
    private String rg;

    @Column(name = "rg_issuer", length = 20)
    private String rgIssuer;

    @Column(name = "rg_issue_date")
    private LocalDate rgIssueDate;

    @Column(name = "nationality", length = 50)
    private String nationality = "Brasileira";

    @Column(name = "marital_status", length = 50)
    private MaritalStatus maritalStatus;

    // ── Contato ──

    @Email
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "is_whatsapp", nullable = false)
    private Boolean isWhatsapp = true;

    @Column(name = "reference_phone", length = 20)
    private String referencePhone;

    @Column(name = "reference_responsible")
    private String referenceResponsible;

    // ── Dados profissionais / previdenciários ──

    @Column(name = "profession", length = 100)
    private String profession;

    @Column(name = "nit_pis", unique = true, length = 20)
    private String nitPis;

    @Column(name = "ctps", length = 30)
    private String ctps;

    @Column(name = "ctps_series", length = 20)
    private String ctpsSeries;

    @Column(name = "benefit_number", unique = true, length = 30)
    private String beneficiaryNumber;

    @Column(name = "contribution_time")
    private String contributionTime;

    /** Derivado de contributionTime via ContributionTimeParser - nunca escrito direto pela API. */
    @Column(name = "contribution_in_months")
    private Integer contributionInMonths;

    @Column(name = "has_disability", nullable = false)
    private Boolean hasDisability = false;

    // ── Gestão do caso ──

    @Column(name = "client_type", nullable = false, length = 20)
    private ClientType clientType = ClientType.POTENCIAL;

    @Column(name = "not_billable", nullable = false)
    private Boolean notBillable = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    // ── Auditoria ──

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.updatedAt == null) this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public Situation getSituation() { return situation; }
    public void setSituation(Situation situation) { this.situation = situation; }

    public BenefitType getBenefit() { return benefit; }
    public void setBenefit(BenefitType benefit) { this.benefit = benefit; }

    public String getRg() { return rg; }
    public void setRg(String rg) { this.rg = rg; }

    public String getRgIssuer() { return rgIssuer; }
    public void setRgIssuer(String rgIssuer) { this.rgIssuer = rgIssuer; }

    public LocalDate getRgIssueDate() { return rgIssueDate; }
    public void setRgIssueDate(LocalDate rgIssueDate) { this.rgIssueDate = rgIssueDate; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public MaritalStatus getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(MaritalStatus maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getIsWhatsapp() { return isWhatsapp; }
    public void setIsWhatsapp(Boolean isWhatsapp) { this.isWhatsapp = isWhatsapp; }

    public String getReferencePhone() { return referencePhone; }
    public void setReferencePhone(String referencePhone) { this.referencePhone = referencePhone; }

    public String getReferenceResponsible() { return referenceResponsible; }
    public void setReferenceResponsible(String referenceResponsible) { this.referenceResponsible = referenceResponsible; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getNitPis() { return nitPis; }
    public void setNitPis(String nitPis) { this.nitPis = nitPis; }

    public String getCtps() { return ctps; }
    public void setCtps(String ctps) { this.ctps = ctps; }

    public String getCtpsSeries() { return ctpsSeries; }
    public void setCtpsSeries(String ctpsSeries) { this.ctpsSeries = ctpsSeries; }

    public String getBeneficiaryNumber() { return beneficiaryNumber; }
    public void setBeneficiaryNumber(String beneficiaryNumber) { this.beneficiaryNumber = beneficiaryNumber; }

    public String getContributionTime() { return contributionTime; }
    public void setContributionTime(String contributionTime) { this.contributionTime = contributionTime; }

    public Integer getContributionInMonths() { return contributionInMonths; }
    public void setContributionInMonths(Integer contributionInMonths) { this.contributionInMonths = contributionInMonths; }

    public Boolean getHasDisability() { return hasDisability; }
    public void setHasDisability(Boolean hasDisability) { this.hasDisability = hasDisability; }

    public ClientType getClientType() { return clientType; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }

    public Boolean getNotBillable() { return notBillable; }
    public void setNotBillable(Boolean notBillable) { this.notBillable = notBillable; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getResponsibleUserId() { return responsibleUserId; }
    public void setResponsibleUserId(UUID responsibleUserId) { this.responsibleUserId = responsibleUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
