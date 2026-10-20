package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Lançamento de receita do escritório — honorário, parecer avulso, reembolso.
 *
 * <p>Não confundir com {@code ClientPaymentRequestDTO}: aquele registra o que o INSS paga ao
 * cliente, e não passa pelo caixa do escritório. O formato é parecido; o dinheiro é de outra
 * pessoa.
 */
@Getter
@Setter
public class OfficeRevenueRequestDTO {

    @Schema(
            description = "De onde vem a receita",
            example = "Honorário de êxito — 30% dos atrasados")
    @NotBlank
    @Size(max = 255)
    private String description;

    @Schema(description = "Valor, sempre positivo")
    @NotNull
    @DecimalMin(value = "0.01", message = "O valor da receita precisa ser maior que zero.")
    private BigDecimal amount;

    @Schema(description = "Vencimento (yyyy-MM-dd)")
    @NotNull
    private LocalDate dueDate;

    @Schema(description = "Data do recebimento; informada, marca a receita como paga")
    private LocalDate paidDate;

    @Schema(description = "Situação (Pendente, Pago, Cancelado). Omitida, nasce Pendente.")
    @Size(max = 20)
    private String status;

    @Schema(description = "Forma de recebimento (Pix, Boleto, ...)")
    @Size(max = 20)
    private String paymentMethod;

    @Schema(description = "Cliente de origem, quando houver. Nem toda receita vem de cliente.")
    private UUID clientId;

    @Schema(
            description =
                    "Parcela do INSS que originou o honorário de êxito. É o que permite conferir"
                            + " \"30% dos atrasados\" contra o valor de fato, meses depois.")
    private UUID sourcePaymentId;

    @Size(max = 2000)
    private String notes;
}
