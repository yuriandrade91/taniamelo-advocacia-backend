package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload de atualização completa (PUT /clients/{clientId}). Mesmas regras do create: PUT é
 * substituição total dos campos editáveis - para atualização parcial use PATCH /clients/{clientId}.
 */
public class ClientUpdateRequestDTO implements ClientWritableFields {

    @NotBlank private String fullName;

    @NotNull private LocalDate birthDate;

    @NotBlank @ValidCPF private String cpf;

    @NotBlank private String motherName;

    @NotBlank private String mobilePhone;

    /**
     * Ausente significa "mantém a que está gravada".
     *
     * <p>Deixou de ser obrigatória na edição quando a senha saiu do {@code GET}: o cliente da API
     * não a recebe mais, logo não tem como devolvê-la num PUT. Exigi-la aqui obrigaria a tela a
     * pedir a senha de novo a cada correção de endereço.
     */
    private String inssPassword;

    @NotNull private Gender gender;

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

    /**
     * Tempo de contribuição em três campos, e não numa frase: "nao informado" virava 0 e "300000000
     * anos" virava -694967296 quando isto era texto interpretado por regex. Faixas em
     * TempoDeContribuicao (mês de 30 dias, ano de 12 meses).
     */
    @Min(value = 0, message = "Anos de contribuição não pode ser negativo")
    @Max(value = 130, message = "Anos de contribuição não pode passar de 130")
    private Integer contributionYears;

    @Min(value = 0, message = "Meses de contribuição não pode ser negativo")
    @Max(value = 11, message = "Meses de contribuição vai de 0 a 11 - 12 meses são 1 ano")
    private Integer contributionMonths;

    @Min(value = 0, message = "Dias de contribuição não pode ser negativo")
    @Max(value = 29, message = "Dias de contribuição vai de 0 a 29 - 30 dias são 1 mês")
    private Integer contributionDays;

    private Boolean notBillable;

    private String rgIssuer;

    private LocalDate rgIssueDate;

    private String nationality;

    private Boolean isWhatsapp;

    private Boolean hasDisability;

    private String notes;

    private UUID responsibleUserId;

    private ClientType clientType;

    @Override
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    @Override
    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    @Override
    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    @Override
    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    @Override
    public String getInssPassword() {
        return inssPassword;
    }

    public void setInssPassword(String inssPassword) {
        this.inssPassword = inssPassword;
    }

    @Override
    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    @Override
    public String getRg() {
        return rg;
    }

    public void setRg(String rg) {
        this.rg = rg;
    }

    @Override
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getReferencePhone() {
        return referencePhone;
    }

    public void setReferencePhone(String referencePhone) {
        this.referencePhone = referencePhone;
    }

    @Override
    public String getReferenceResponsible() {
        return referenceResponsible;
    }

    public void setReferenceResponsible(String referenceResponsible) {
        this.referenceResponsible = referenceResponsible;
    }

    @Override
    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    @Override
    public BenefitType getBenefit() {
        return benefit;
    }

    public void setBenefit(BenefitType benefit) {
        this.benefit = benefit;
    }

    @Override
    public Situation getSituation() {
        return situation;
    }

    public void setSituation(Situation situation) {
        this.situation = situation;
    }

    @Override
    public String getBeneficiaryNumber() {
        return beneficiaryNumber;
    }

    public void setBeneficiaryNumber(String beneficiaryNumber) {
        this.beneficiaryNumber = beneficiaryNumber;
    }

    @Override
    public String getNitPis() {
        return nitPis;
    }

    public void setNitPis(String nitPis) {
        this.nitPis = nitPis;
    }

    @Override
    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    @Override
    public String getCtps() {
        return ctps;
    }

    public void setCtps(String ctps) {
        this.ctps = ctps;
    }

    @Override
    public String getCtpsSeries() {
        return ctpsSeries;
    }

    public void setCtpsSeries(String ctpsSeries) {
        this.ctpsSeries = ctpsSeries;
    }

    @Override
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
}
