package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha da visão consolidada de pagamentos — o que os clientes têm a receber do INSS.
 *
 * <p>É o {@code ClientPaymentResponseDTO} mais o cliente ao lado. O nome vem desnormalizado de
 * propósito: sem ele a tela precisaria de uma consulta por linha para mostrar de quem é o valor, e
 * o N+1 mudaria de lugar em vez de sumir.
 *
 * <p>É a projeção das <b>duas</b> rotas que listam parcelas: {@code GET /payments} e a aba {@code
 * GET /clients/{clientId}/payments}. A aba devolvia o DTO do detalhe, o mesmo do {@code GET
 * /clients/{clientId}/payments/{paymentId}} — carregando observação e as quatro colunas de autoria
 * em cada linha, e apagando a diferença entre listar e detalhar. Observação e autoria agora só no
 * detalhe.
 *
 * <p>Na aba, {@code clientId} e {@code clientName} são redundantes com a URL. Ficam porque uma
 * projeção só para as duas rotas vale mais do que dois DTOs quase iguais sobre a mesma tabela — foi
 * o mesmo raciocínio que fez a consulta ser uma só em {@code PaymentSpecs}.
 *
 * <p><b>Não é honorário.</b> Este dinheiro é do cliente e não passa pelo caixa do escritório — a
 * receita do escritório é {@code OfficeRevenue}. As duas têm quase o mesmo formato, e confundi-las
 * já fez a Carteira mostrar o dinheiro dos clientes como faturamento.
 */
public class PaymentListItemDTO {

    private UUID id;

    @Schema(description = "Cliente a quem o valor pertence")
    private UUID clientId;

    private String clientName;

    private String description;

    @Schema(description = "Parcela N de um total — as duas juntas formam o \"2/6\" da grade")
    private Integer installmentNumber;

    private Integer installmentTotal;

    private LocalDate dueDate;
    private LocalDate paidDate;
    private String paymentMethod;
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

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public void setInstallmentNumber(Integer installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public Integer getInstallmentTotal() {
        return installmentTotal;
    }

    public void setInstallmentTotal(Integer installmentTotal) {
        this.installmentTotal = installmentTotal;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }
}
