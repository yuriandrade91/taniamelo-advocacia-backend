package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Receita do escritório como a tela da Carteira a lê. */
@JsonPropertyOrder({"id"})
@Getter
@Setter
public class OfficeRevenueResponseDTO {

    private UUID id;
    private String description;
    private BigDecimal amount;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private String status;
    private String paymentMethod;
    private UUID clientId;

    @Schema(
            description =
                    "Nome do cliente, desnormalizado. Sem ele a tela faria uma consulta por linha"
                            + " para mostrar de quem é a receita — o N+1 mudaria de lugar, não"
                            + " sumiria.")
    private String clientName;

    private UUID sourcePaymentId;
    private String notes;
    private boolean overdue;
    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;
}
