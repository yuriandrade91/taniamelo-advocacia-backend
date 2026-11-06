package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.validation.ValidCPF;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload de atualização completa (PUT /clients/{clientId}). Mesmas regras do create: PUT é
 * substituição total dos campos editáveis - para atualização parcial use PATCH /clients/{clientId}.
 */
@Setter
@Getter
public class ClientUpdateRequestDTO implements ClientWritableFields {

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

    @Size(max = 255)
    @NotBlank
    private String motherName;

    @Size(max = 20)
    @NotBlank
    private String mobilePhone;

    /**
     * Ausente significa "mantém a que está gravada".
     *
     * <p>Deixou de ser obrigatória na edição quando a senha saiu do {@code GET}: o cliente da API
     * não a recebe mais, logo não tem como devolvê-la num PUT. Exigi-la aqui obrigaria a tela a
     * pedir a senha de novo a cada correção de endereço.
     */
    @Size(max = 255)
    private String inssPassword;

    @NotNull private Gender gender;

    @Size(max = 20)
    private String rg;

    @Size(max = 255)
    @Email
    private String email;

    @Size(max = 20)
    private String referencePhone;

    @Size(max = 255)
    private String referenceResponsible;

    private MaritalStatus maritalStatus;

    @NotNull private BenefitType benefit;

    @NotNull private Situation situation;

    /**
     * Etapa do requerimento no INSS. Anulável: nulo significa "ainda não protocolado", estado
     * legítimo de cliente em prospecção ou planejamento. Não confundir com {@code situation}, que é
     * a esteira interna do escritório.
     */
    private RequestStage requestStage;

    /**
     * Por que a etapa no INSS mudou - a mesma regra do PATCH: obrigatória quando {@code
     * requestStage} muda para exigência, indeferimento ou recurso. Sem ela o PUT seria a porta dos
     * fundos da regra. Mandar a etapa que já está gravada não é mudança e não pede nada.
     */
    @Size(max = 1000)
    private String requestStageJustification;

    @Size(max = 30)
    private String beneficiaryNumber;

    @Size(max = 20)
    private String nitPis;

    @Size(max = 100)
    private String profession;

    @Size(max = 30)
    private String ctps;

    @Size(max = 20)
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

    @Size(max = 20)
    private String rgIssuer;

    @PastOrPresent(message = "Data de emissão do RG não pode estar no futuro")
    private LocalDate rgIssueDate;

    @Size(max = 50)
    private String nationality;

    private Boolean isWhatsapp;

    private Boolean hasDisability;

    private String notes;

    private UUID responsibleUserId;

    private ClientType clientType;

    @Override
    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
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
}
