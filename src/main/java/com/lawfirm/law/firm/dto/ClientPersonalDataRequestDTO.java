package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Aba "Dados pessoais": identidade e contato. Dados profissionais têm endpoint próprio
 * (/professional-data); endereços idem (/addresses).
 */
public class ClientPersonalDataRequestDTO {

    @Size(max = 255)
    @NotBlank
    private String fullName;

    /**
     * No passado, sempre. Sem isto, {@code 2090-01-01} era aceito com 201 e a ficha passava a
     * mostrar {@code age: -63} - verificado rodando.
     */
    @Past(message = "Data de nascimento precisa estar no passado")
    @NotNull
    private LocalDate birthDate;

    @Size(max = 14)
    @NotBlank
    @ValidCPF
    private String cpf;

    @Size(max = 20)
    private String rg;

    @Size(max = 20)
    private String rgIssuer;

    @PastOrPresent(message = "Data de emissão do RG não pode estar no futuro")
    private LocalDate rgIssueDate;

    @Size(max = 255)
    @NotBlank
    private String motherName;

    @NotNull private Gender gender;

    private MaritalStatus maritalStatus;

    @Size(max = 50)
    private String nationality;

    @Size(max = 20)
    @NotBlank
    private String mobilePhone;

    private Boolean isWhatsapp;

    @Size(max = 20)
    private String referencePhone;

    @Size(max = 255)
    private String referenceResponsible;

    @Size(max = 255)
    @Email
    private String email;

    private Boolean hasDisability;

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

    public String getRg() {
        return rg;
    }

    public void setRg(String rg) {
        this.rg = rg;
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

    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public Boolean getIsWhatsapp() {
        return isWhatsapp;
    }

    public void setIsWhatsapp(Boolean isWhatsapp) {
        this.isWhatsapp = isWhatsapp;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getHasDisability() {
        return hasDisability;
    }

    public void setHasDisability(Boolean hasDisability) {
        this.hasDisability = hasDisability;
    }
}
