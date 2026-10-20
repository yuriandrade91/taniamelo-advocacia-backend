package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({
    "clientId",
    "fullName",
    "cpf",
    "mobilePhone",
    "benefit",
    "situation",
    "clientType",
    "notBillable",
    "beneficiaryNumber",
    "createdAt",
    "updatedAt"
})
@Getter
@Setter
public class ClientListResponseDTO {
    private UUID clientId;
    private String fullName;
    private String cpf;
    private String mobilePhone;
    private BenefitType benefit;
    private Situation situation;

    /**
     * Etapa do requerimento no INSS. Anulável: nulo significa "ainda não protocolado", estado
     * legítimo de cliente em prospecção ou planejamento. Não confundir com {@code situation}, que é
     * a esteira interna do escritório.
     */
    private RequestStage requestStage;

    private ClientType clientType;

    /**
     * Marca de "sem arrecadação", o mesmo campo filtrável por {@code ?notBillable=} na listagem.
     */
    private Boolean notBillable;

    private Instant createdAt;
    private String beneficiaryNumber;
    private Instant updatedAt;

    /**
     * Quando foi excluído. Nulo na listagem normal - só a lixeira ({@code GET /clients/deleted})
     * devolve registros com isto preenchido.
     */
    private Instant deletedAt;
}
