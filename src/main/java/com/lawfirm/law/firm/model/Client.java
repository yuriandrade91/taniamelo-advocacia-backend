package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import com.lawfirm.law.firm.audit.Auditable;
import com.lawfirm.law.firm.model.converter.CryptoConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "clients")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditLogListener.class)
public class Client implements Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    @Setter
    private UUID id;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    @Getter
    @Setter
    private String fullName;

    // ============================================
    // OBRIGATÓRIOS
    // ============================================
    @Column(name = "birth_date", nullable = false)
    @Getter
    @Setter
    private LocalDate birthDate;

    @Column(name = "birth_mmdd", insertable = false, updatable = false)
    private Short birthMmdd;

    @NotBlank
    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    @Getter
    @Setter
    private String cpf;

    @NotBlank
    @Column(name = "mother_name", nullable = false)
    @Getter
    @Setter
    private String motherName;

    @NotBlank
    @Column(name = "mobile_phone", nullable = false, unique = true, length = 20)
    @Getter
    @Setter
    private String mobilePhone;

    @NotBlank
    @Convert(converter = CryptoConverter.class)
    @Column(name = "inss_password", nullable = false, length = 255)
    @Getter
    @Setter
    private String inssPassword;

    @NotNull
    @Column(name = "gender", nullable = false, length = 10)
    @Getter
    @Setter
    private Gender gender;

    @NotNull
    @Column(name = "situation", length = 100)
    @Getter
    @Setter
    private Situation situation;

    @NotNull
    @Column(name = "benefit", length = 100)
    @Getter
    @Setter
    private BenefitType benefit;

    @Column(name = "request_stage", length = 100)
    @Getter
    @Setter
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
    @Getter
    @Setter
    private String rg;

    @Email
    @Column(name = "email", unique = true, length = 255)
    @Getter
    @Setter
    private String email;

    @Column(name = "reference_phone", unique = true, length = 20)
    @Getter
    @Setter
    private String referencePhone;

    @Column(name = "reference_responsible")
    @Getter
    @Setter
    private String referenceResponsible;

    @Column(name = "marital_status", length = 50)
    private MaritalStatus
            maritalStatus; // Solteiro(a), Casado(a), Separada(a), Divorciado(a), Viúvo(a)

    @Column(name = "benefit_number", unique = true, length = 30)
    @Getter
    @Setter
    private String beneficiaryNumber;

    @Column(name = "nit_pis", unique = true, length = 20)
    @Getter
    @Setter
    private String nitPis;

    @Column(name = "profession")
    @Getter
    @Setter
    private String profession;

    @Column(name = "ctps", unique = true, length = 30)
    @Getter
    @Setter
    private String ctps;

    @Column(name = "ctps_series", unique = true, length = 20)
    @Getter
    @Setter
    private String ctpsSeries;

    @Column(name = "contribution_years")
    @Getter
    @Setter
    private Integer contributionYears;

    @Column(name = "contribution_months")
    @Getter
    @Setter
    private Integer contributionMonths;

    @Column(name = "contribution_days")
    @Getter
    @Setter
    private Integer contributionDays;

    @Column(name = "contribution_in_months")
    @Getter
    @Setter
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
    @Getter
    @Setter
    private String rgIssuer;

    @Column(name = "rg_issue_date")
    @Getter
    @Setter
    private LocalDate rgIssueDate;

    @Column(name = "nationality", length = 50)
    @Getter
    @Setter
    private String nationality = "Brasileira";

    @Column(name = "is_whatsapp", nullable = false)
    @Getter
    @Setter
    private Boolean isWhatsapp = true;

    @Column(name = "has_disability", nullable = false)
    @Getter
    @Setter
    private Boolean hasDisability = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Getter
    @Setter
    private String notes;

    @Column(name = "responsible_user_id")
    @Getter
    @Setter
    private UUID responsibleUserId;

    @Column(name = "client_type", nullable = false, length = 20)
    @Getter
    @Setter
    private ClientType clientType = ClientType.POTENCIAL;

    @Column(name = "created_at", nullable = false)
    @Getter
    @Setter
    private Instant createdAt;

    @Column(name = "updated_at")
    @Getter
    @Setter
    private Instant updatedAt;

    @Column(name = "not_billable", nullable = false)
    @Getter
    @Setter
    private Boolean notBillable = false;

    @Column(name = "created_by")
    @Getter
    @Setter
    private UUID createdBy;

    @Column(name = "updated_by")
    @Getter
    @Setter
    private UUID updatedBy;

    @Column(name = "deleted_at")
    @Getter
    @Setter
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

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
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
}
