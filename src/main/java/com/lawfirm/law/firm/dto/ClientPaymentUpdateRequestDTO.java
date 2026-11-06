package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * PATCH parcial de uma parcela: só os campos enviados são alterados. Regras de consistência entre
 * status/paidDate ficam no service (ver {@code ClientPaymentService#update}) - por exemplo, marcar
 * status=Pago sem informar paidDate assume a data de hoje.
 */
@Getter
@Setter
public class ClientPaymentUpdateRequestDTO {

    @Size(max = 255)
    private String description;

    private BigDecimal amount;
    private Integer installmentNumber;
    private Integer installmentTotal;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private String status;

    @Size(max = 20)
    private String paymentMethod;

    private String notes;
}
