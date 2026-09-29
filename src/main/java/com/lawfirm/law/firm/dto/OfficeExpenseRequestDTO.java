package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lançamento de despesa do escritório.
 *
 * <p>Enums entram como nome da constante OU rótulo PT-BR, como no resto da API. Valor desconhecido
 * responde 400 nomeando o campo, em vez de virar nulo em silêncio — despesa sem categoria some do
 * gráfico de composição, e ninguém procura o que nunca apareceu.
 */
public class OfficeExpenseRequestDTO {

    @Schema(description = "O que foi pago", example = "Aluguel da sala 302")
    @NotBlank
    @Size(max = 255)
    private String description;

    @Schema(description = "Valor, sempre positivo")
    @NotNull
    @DecimalMin(value = "0.01", message = "O valor da despesa precisa ser maior que zero.")
    private BigDecimal amount;

    @Schema(
            description = "Categoria (nome da constante ou rótulo)",
            example = "Aluguel e condomínio")
    @Size(max = 40)
    private String category;

    @Schema(description = "Fornecedor ou prestador")
    @Size(max = 255)
    private String supplier;

    @Schema(description = "Vencimento (yyyy-MM-dd)")
    @NotNull
    private LocalDate dueDate;

    @Schema(description = "Data do pagamento; informada, marca a despesa como paga")
    private LocalDate paidDate;

    @Schema(description = "Situação (Pendente, Pago, Cancelado). Omitida, nasce Pendente.")
    @Size(max = 20)
    private String status;

    @Schema(description = "Forma de pagamento (Pix, Boleto, ...)")
    @Size(max = 20)
    private String paymentMethod;

    @Size(max = 2000)
    private String notes;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
