package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@JsonPropertyOrder({"id"})
@Getter
@Setter
public class ClientPaymentResponseDTO {

    private UUID id;
    private String description;
    private BigDecimal amount;
    private Integer installmentNumber;
    private Integer installmentTotal;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private String status;
    private String paymentMethod;
    private String notes;

    /** Derivado em runtime: PENDENTE com vencimento no passado. Nunca persistido. */
    private boolean overdue;

    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;
}
