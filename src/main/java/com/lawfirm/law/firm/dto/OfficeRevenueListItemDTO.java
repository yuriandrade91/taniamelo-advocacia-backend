package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma linha da lista de receitas (entradas) — só o que a tela mostra na grade.
 *
 * <p>Mesmo motivo do {@code OfficeExpenseListItemDTO}: {@code GET /revenues} repetia o {@code
 * OfficeRevenueResponseDTO} do detalhe, trazendo observação, forma de pagamento, a parcela de
 * origem e as quatro colunas de autoria em toda linha.
 *
 * <p>A contraparte vem como {@code clientName} desnormalizado, e não como objeto: a grade mostra de
 * quem é a entrada, e resolver isso por linha devolveria o N+1 que o {@code join} do filtro já
 * evita. O {@code clientId} ficou no detalhe — a linha abre a receita, não a ficha do cliente.
 *
 * <p>O que ficou só no detalhe: {@code clientId}, {@code paymentMethod}, {@code sourcePaymentId},
 * {@code notes} e a autoria ({@code createdBy}, {@code createdAt}, {@code updatedBy}, {@code
 * updatedAt}).
 */
@JsonPropertyOrder({"id"})
@Getter
@Setter
public class OfficeRevenueListItemDTO {

    private UUID id;
    private String description;

    @Schema(description = "Cliente de quem veio a entrada, desnormalizado para a grade")
    private String clientName;

    private LocalDate dueDate;

    @Schema(description = "Data do recebimento. Nula enquanto a entrada não foi recebida.")
    private LocalDate paidDate;

    private BigDecimal amount;
    private String status;

    @Schema(description = "Pendente e vencido. Calculado no servidor, nunca no navegador.")
    private boolean overdue;
}
