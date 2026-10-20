package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Despesa do escritório como a tela da Carteira a lê. Enums saem pelo rótulo PT-BR. */
@Getter
@Setter
public class OfficeExpenseResponseDTO {

    private UUID id;
    private String description;
    private BigDecimal amount;
    private String category;
    private String supplier;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private String status;
    private String paymentMethod;
    private String notes;

    @Schema(
            description =
                    "Pendente e vencida. Calculado no servidor: o relógio do navegador é do"
                            + " usuário, e duas fontes para \"hoje\" fazem a mesma linha aparecer"
                            + " vencida numa tela e em dia noutra.")
    private boolean overdue;

    private UUID createdBy;
    private Instant createdAt;
    private UUID updatedBy;
    private Instant updatedAt;
}
