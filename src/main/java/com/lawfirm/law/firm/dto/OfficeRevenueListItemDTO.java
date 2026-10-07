package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }
}
