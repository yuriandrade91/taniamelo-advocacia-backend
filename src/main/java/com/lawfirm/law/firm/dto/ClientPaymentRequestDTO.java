package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Corpo de POST de uma parcela que o cliente tem a receber. Sempre nasce como PENDENTE.
 *
 * <p>Honorário não entra aqui - é {@code OfficeRevenueRequestDTO}.
 */
@Getter
@Setter
public class ClientPaymentRequestDTO {

    @Size(max = 255)
    @NotBlank
    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "O valor da parcela precisa ser maior que zero.")
    private BigDecimal amount;

    private Integer installmentNumber;
    private Integer installmentTotal;

    @NotNull private LocalDate dueDate;

    @Size(max = 20)
    private String paymentMethod;

    private String notes;

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
