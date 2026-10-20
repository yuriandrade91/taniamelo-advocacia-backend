package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Filtros da lista de despesas do escritório.
 *
 * <p>Período, situação, forma de pagamento e paginação vêm de {@link FinanceSearchParams}.
 */
@Getter
@Setter
public class OfficeExpenseSearchParams extends FinanceSearchParams {

    @Schema(description = "Busca na descrição ou no fornecedor")
    private String searchTerm;

    @Schema(description = "Categoria(s): nome da constante ou rótulo")
    private List<String> category;
}
