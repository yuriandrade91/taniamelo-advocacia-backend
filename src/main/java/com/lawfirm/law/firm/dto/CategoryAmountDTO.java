package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Quanto foi pago numa categoria de despesa, no período consultado. */
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
