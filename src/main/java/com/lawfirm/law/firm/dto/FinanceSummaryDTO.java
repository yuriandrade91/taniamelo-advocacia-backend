package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Totais de um período, nos três baldes que as telas financeiras usam.
 *
 * <p>Os baldes são <b>disjuntos</b>, e a regra é a mesma em pagamentos, despesas e receitas:
 *
 * <ul>
 *   <li>{@code overdue} - Pendente e vencimento no passado;
 *   <li>{@code upcoming} - Pendente e vencimento hoje ou no futuro;
 *   <li>{@code paid} - Pago.
 * </ul>
 *
 * <p>Cancelado fica fora dos três e fora do total: é lançamento que deixou de existir para efeito
 * de caixa, e somá-lo em qualquer balde faria o resultado do escritório contar dinheiro que ninguém
 * espera.
 *
 * <p>{@code totalAmount} é redundante de propósito - é a soma dos três. Se um dia a conta não
 * fechar, a regra divergiu entre duas pontas, e é melhor descobrir por uma soma que não bate do que
 * por um relatório errado.
 */
public class FinanceSummaryDTO {

    @Schema(description = "Soma do que está vencido e não pago")
    private BigDecimal overdueAmount = BigDecimal.ZERO;

    @Schema(description = "Quantidade de lançamentos vencidos e não pagos")
    private long overdueCount;

    @Schema(description = "Soma do que ainda vai vencer")
    private BigDecimal upcomingAmount = BigDecimal.ZERO;

    @Schema(description = "Quantidade de lançamentos a vencer")
    private long upcomingCount;

    @Schema(description = "Soma do que já foi pago")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Schema(description = "Quantidade de lançamentos pagos")
    private long paidCount;

    @Schema(description = "overdueAmount + upcomingAmount + paidAmount (Cancelado fora)")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Schema(
            description =
                    "Composição do gasto PAGO no período, por categoria. Só em despesas; nulo nas"
                            + " demais rotas.")
    private List<CategoryAmountDTO> byCategory;

    public BigDecimal getOverdueAmount() {
        return overdueAmount;
    }

    public void setOverdueAmount(BigDecimal overdueAmount) {
        this.overdueAmount = overdueAmount;
    }

    public long getOverdueCount() {
        return overdueCount;
    }

    public void setOverdueCount(long overdueCount) {
        this.overdueCount = overdueCount;
    }

    public BigDecimal getUpcomingAmount() {
        return upcomingAmount;
    }

    public void setUpcomingAmount(BigDecimal upcomingAmount) {
        this.upcomingAmount = upcomingAmount;
    }

    public long getUpcomingCount() {
        return upcomingCount;
    }

    public void setUpcomingCount(long upcomingCount) {
        this.upcomingCount = upcomingCount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public long getPaidCount() {
        return paidCount;
    }

    public void setPaidCount(long paidCount) {
        this.paidCount = paidCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public List<CategoryAmountDTO> getByCategory() {
        return byCategory;
    }

    public void setByCategory(List<CategoryAmountDTO> byCategory) {
        this.byCategory = byCategory;
    }
}
