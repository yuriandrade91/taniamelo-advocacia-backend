package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.security.RequerAdmin;
import com.lawfirm.law.firm.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que os clientes têm a receber do INSS, visto pelo escritório.
 *
 * <p>Top-level e tenant-scoped, como a agenda: a pergunta é do escritório, não de um cliente. É
 * <b>somente leitura</b> — criar e editar continuam em {@code /clients/{clientId}/payments}, porque
 * uma parcela pertence a um cliente.
 *
 * <p><b>Este dinheiro não é do escritório.</b> São atrasados da concessão, benefício mensal,
 * parcela de acordo — dinheiro do cliente, que não passa pelo caixa. O faturamento do escritório
 * está em {@code /api/v1/revenues}. Somar um como se fosse o outro já fez a Carteira mostrar saldo
 * plausível, positivo e falso.
 *
 * <p>Restrito a ADMIN: dinheiro é o único recorte que nem advogado atravessa.
 */
@Tag(
        name = "Financeiro — pagamentos aos clientes",
        description = "Visão consolidada do que os clientes têm a receber do INSS")
@SecurityRequirements({
    @SecurityRequirement(name = "bearerAuth"),
    @SecurityRequirement(name = "tenantHeader")
})
@RestController
@RequestMapping("/api/v1/payments")
@RequerAdmin
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @Operation(
            summary = "Listar pagamentos de todos os clientes",
            description =
                    """
                    Paginada, ordenada por vencimento (o que vence primeiro aparece primeiro), com \
                    o nome do cliente em cada linha.

                    Os filtros de período são mutuamente exclusivos: `dueFrom`/`dueTo` recortam por \
                    vencimento e `paidFrom`/`paidTo` por pagamento. Mandar os dois responde 400.""")
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentListItemDTO>> list(
            @ParameterObject PaymentSearchParams params) {
        var page = service.list(params);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(
            summary = "Totais do período",
            description =
                    """
                    Três baldes **disjuntos**: vencido e não pago, a vencer, e pago. Cancelado fica \
                    fora dos três e fora do total.

                    Não aceita paginação de propósito — é justamente o que ele resolve: somar a \
                    página traria o total de 10 linhas, não do período.""")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FinanceSummaryDTO>> summary(
            @ParameterObject PaymentSearchParams params) {
        return ResponseEntity.ok(ApiResponse.successObject(service.summary(params)));
    }

    @Operation(
            summary = "Série mensal para o gráfico",
            description =
                    """
                    Um ponto por mês entre `from` e `to`, **inclusive os meses sem movimento**, \
                    zerados — omiti-los faria o gráfico colar dois meses distantes lado a lado e \
                    sumir justamente com o mês vazio.

                    `basis=due` (padrão) agrupa por vencimento; `basis=paid` agrupa por data de \
                    pagamento e só conta o que foi pago. Com `paid`, os campos de vencido e a \
                    vencer vêm zerados por definição.""")
    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<FinanceTimelinePointDTO>> timeline(
            @Parameter(description = "Início (yyyy-MM-dd)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Fim (yyyy-MM-dd)")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @Parameter(description = "due (competência) ou paid (caixa)")
                    @RequestParam(defaultValue = "due")
                    String basis) {
        List<FinanceTimelinePointDTO> serie =
                service.timeline(from, to, Serie.porPagamento("basis", basis));
        return ResponseEntity.ok(ApiResponse.successList(serie));
    }
}
