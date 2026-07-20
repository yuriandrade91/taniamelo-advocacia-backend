package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Aba "Dados profissionais": profissão, vínculos e credenciais previdenciárias. contributionTime é
 * texto livre (ex.: "3 anos, 10 meses, 22 dias") - o total em meses é derivado no servidor
 * (contributionInMonths, somente leitura).
 */
public class ClientProfessionalDataRequestDTO {

    private String profession;

    private String nitPis;

    private String ctps;

    private String ctpsSeries;

    private String contributionTime;

    private String beneficiaryNumber;

    @NotBlank private String inssPassword;

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getNitPis() {
        return nitPis;
    }

    public void setNitPis(String nitPis) {
        this.nitPis = nitPis;
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

    public String getContributionTime() {
        return contributionTime;
    }

    public void setContributionTime(String contributionTime) {
        this.contributionTime = contributionTime;
    }

    public String getBeneficiaryNumber() {
        return beneficiaryNumber;
    }

    public void setBeneficiaryNumber(String beneficiaryNumber) {
        this.beneficiaryNumber = beneficiaryNumber;
    }

    public String getInssPassword() {
        return inssPassword;
    }

    public void setInssPassword(String inssPassword) {
        this.inssPassword = inssPassword;
    }
}
