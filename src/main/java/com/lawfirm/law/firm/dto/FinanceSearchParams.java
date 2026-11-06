package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Filtros comuns às listas financeiras (pagamentos, despesas e receitas).
 *
 * <p><b>Competência e caixa não se misturam numa chamada.</b> {@code dueFrom}/{@code dueTo}
 * recortam por vencimento ("o que vence no mês"); {@code paidFrom}/{@code paidTo}, por data de
 * pagamento ("o que entrou e saiu no mês"). Mandar os dois pares responde 400, em vez de o servidor
 * escolher um: somar recebimento por vencimento e apresentar como "entrou" conta dinheiro que não
 * chegou, e o erro é silencioso — os números parecem plausíveis e só aparecem quando alguém confere
 * com o extrato.
 *
 * <p>Listas aceitam o parâmetro repetido, sem colchetes ({@code ?status=Pendente&status=Pago}).
 */
@Getter
@Setter
public abstract class FinanceSearchParams {

    @Schema(description = "Número da página (1-based)", defaultValue = "1")
    private int pageNumber = 1;

    @Schema(description = "Tamanho da página (máximo 100)", defaultValue = "10")
    private int pageSize = 10;

    @Schema(description = "Início do intervalo de VENCIMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate dueFrom;

    @Schema(description = "Fim do intervalo de VENCIMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate dueTo;

    @Schema(description = "Início do intervalo de PAGAMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate paidFrom;

    @Schema(description = "Fim do intervalo de PAGAMENTO (yyyy-MM-dd), inclusivo")
    private LocalDate paidTo;

    @Schema(description = "Situação(ões): nome da constante ou rótulo")
    private List<String> status;

    @Schema(description = "Forma(s) de pagamento: nome da constante ou rótulo")
    private List<String> paymentMethod;
}
