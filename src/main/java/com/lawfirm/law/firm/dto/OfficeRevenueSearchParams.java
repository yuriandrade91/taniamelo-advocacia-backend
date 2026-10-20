package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Filtros da lista de receitas do escritório.
 *
 * <p>Período, situação, forma de pagamento e paginação vêm de {@link FinanceSearchParams}.
 */
@Getter
@Setter
public class OfficeRevenueSearchParams extends FinanceSearchParams {

    @Schema(description = "Busca na descrição ou no nome do cliente")
    private String searchTerm;

    @Schema(description = "Filtra um cliente específico")
    private UUID clientId;
}
