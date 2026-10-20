package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Filtros da lista consolidada de pagamentos (o que os clientes têm a receber do INSS).
 *
 * <p>Período, situação, forma de pagamento e paginação vêm de {@link FinanceSearchParams}.
 */
@Getter
@Setter
public class PaymentSearchParams extends FinanceSearchParams {

    @Schema(description = "Busca no nome do cliente ou na descrição do pagamento")
    private String searchTerm;

    @Schema(description = "Filtra um cliente específico")
    private UUID clientId;
}
