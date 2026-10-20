package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Quanto foi pago numa categoria de despesa, no período consultado. */
@Getter
@Setter
public class CategoryAmountDTO {

    @Schema(description = "Rótulo da categoria (ex.: Aluguel e condomínio)")
    private String category;

    @Schema(description = "Soma paga na categoria")
    private BigDecimal amount;

    public CategoryAmountDTO() {}

    public CategoryAmountDTO(String category, BigDecimal amount) {
        this.category = category;
        this.amount = amount;
    }
}
