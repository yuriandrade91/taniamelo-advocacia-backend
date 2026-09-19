package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Aba "Dados profissionais": profissão, vínculos e credenciais previdenciárias. O tempo de
 * contribuição vem em três campos (anos, meses, dias); o total em meses e a frase de exibição são
 * derivados no servidor (contributionInMonths e contributionTime, somente leitura).
 */
public class ClientProfessionalDataRequestDTO {

    @Size(max = 100)
    private String profession;

    @Size(max = 20)
    private String nitPis;

    @Size(max = 30)
    private String ctps;

    @Size(max = 20)
    private String ctpsSeries;

    @Min(value = 0, message = "Anos de contribuição não pode ser negativo")
    @Max(value = 130, message = "Anos de contribuição não pode passar de 130")
    private Integer contributionYears;

    @Min(value = 0, message = "Meses de contribuição não pode ser negativo")
    @Max(value = 11, message = "Meses de contribuição vai de 0 a 11 - 12 meses são 1 ano")
    private Integer contributionMonths;

    @Min(value = 0, message = "Dias de contribuição não pode ser negativo")
    @Max(value = 29, message = "Dias de contribuição vai de 0 a 29 - 30 dias são 1 mês")
    private Integer contributionDays;

    @Size(max = 30)
    private String beneficiaryNumber;

    /**
     * Ausente significa "mantém a que está gravada".
     *
     * <p>Deixou de ser obrigatória na edição quando a senha saiu do {@code GET}: o cliente da API
     * não a recebe mais, logo não tem como devolvê-la num PUT. Exigi-la aqui obrigaria a tela a
     * pedir a senha de novo a cada correção de endereço.
     */
    @Size(max = 255)
    private String inssPassword;

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
