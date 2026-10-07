package com.lawfirm.law.firm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha da lista de despesas — só o que a tela mostra na grade.
 *
 * <p>Existe porque {@code GET /expenses} devolvia o {@code OfficeExpenseResponseDTO} inteiro, o
 * mesmo do {@code GET /expenses/{id}}: a lista carregava observação, forma de pagamento e as quatro
 * colunas de autoria em cada linha, que nenhuma coluna da grade usa. Duas rotas com a mesma
 * projeção também apagam a diferença entre elas — se a lista já traz tudo, o detalhe não tem o que
 * detalhar.
 *
 * <p>O {@code id} fica porque é por ele que a linha abre o detalhe, e o {@code overdue} porque
 * "Vencido" não é valor de {@code status} (é {@code Pendente} com vencimento no passado): sem o
 * campo, a grade teria de recalcular a data no navegador, que é justamente o que {@code
 * DueDateRules} centraliza no servidor.
 *
 * <p>O que ficou só no detalhe: {@code paymentMethod}, {@code notes} e a autoria ({@code
 * createdBy}, {@code createdAt}, {@code updatedBy}, {@code updatedAt}).
 */
@JsonPropertyOrder({"id"})
public class OfficeExpenseListItemDTO {

    private UUID id;
    private String description;
    private String supplier;
    private String category;
    private LocalDate dueDate;
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

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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
