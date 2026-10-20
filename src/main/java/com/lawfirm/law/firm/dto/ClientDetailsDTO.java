package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Resposta completa de um cliente (somente saída). Endereços, arquivos, entrevistas e pagamentos
 * são recursos próprios sob /clients/{clientId}/...
 */
@JsonPropertyOrder({"clientId"})
@Getter
@Setter
public class ClientDetailsDTO {

    private UUID clientId;

    private String fullName;

    private LocalDate birthDate;

    private Integer age;

    private String cpf;

    private String motherName;

    private String mobilePhone;

    private Gender gender;

    private String rg;

    private String rgIssuer;

    private LocalDate rgIssueDate;

    private String email;

    private String referencePhone;

    private String referenceResponsible;

    private MaritalStatus maritalStatus;

    private BenefitType benefit;

    private Situation situation;

    /**
     * Etapa do requerimento no INSS. Anulável: nulo significa "ainda não protocolado", estado
     * legítimo de cliente em prospecção ou planejamento. Não confundir com {@code situation}, que é
     * a esteira interna do escritório.
     */
    private RequestStage requestStage;

    private String beneficiaryNumber;

    private String nitPis;

    private String profession;

    private String ctps;

    private String ctpsSeries;

    private Integer contributionYears;

    private Integer contributionMonths;

    private Integer contributionDays;

    /**
     * Derivado: "33 anos, 11 meses e 5 dias". Somente leitura - o que se grava são os três acima.
     */
    private String contributionTime;

    private Integer contributionInMonths;

    private String nationality;

    private Boolean isWhatsapp;

    private Boolean hasDisability;

    private String notes;

    private UUID responsibleUserId;

    private ClientType clientType;

    private Boolean notBillable;

    private Instant createdAt;

    private Instant updatedAt;

    private UUID createdBy;

    private UUID updatedBy;
}
