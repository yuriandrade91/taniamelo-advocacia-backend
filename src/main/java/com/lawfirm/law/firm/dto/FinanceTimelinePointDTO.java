package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Um mês da série que alimenta os gráficos.
 *
 * <p>Meses sem movimento vêm no array, zerados. Omiti-los faria o gráfico colar dois meses
 * distantes lado a lado e sumir justamente com o mês vazio - que costuma ser a informação.
 *
 * <p>Com {@code basis=paid}, {@code overdueAmount} e {@code upcomingAmount} vêm zerados por
 * definição: o que foi pago não está atrasado nem a vencer.
 */
@Getter
@Setter
public class FinanceTimelinePointDTO {

    @Schema(description = "Mês no formato yyyy-MM", example = "2026-05")
    private String month;

    private BigDecimal overdueAmount = BigDecimal.ZERO;
    private BigDecimal upcomingAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;

    public FinanceTimelinePointDTO() {}

    public FinanceTimelinePointDTO(String month) {
        this.month = month;
    }
}
