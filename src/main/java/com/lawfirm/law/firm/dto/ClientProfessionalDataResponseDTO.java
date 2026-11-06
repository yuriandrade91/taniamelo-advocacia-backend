package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({"clientId"})
@Getter
@Setter
public class ClientProfessionalDataResponseDTO {

    private UUID clientId;

    private String profession;

    private String nitPis;

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

    private String beneficiaryNumber;

    private UUID updatedBy;

    private Instant updatedAt;
}
