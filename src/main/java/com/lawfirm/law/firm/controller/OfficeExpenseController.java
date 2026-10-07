package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseListItemDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseRequestDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseResponseDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseSearchParams;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.security.RequerAdmin;
import com.lawfirm.law.firm.service.OfficeExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Despesas do escritório — a metade das saídas da Carteira.
 *
 * <p>Sem cliente: despesa de escritório não pertence a cliente, e custa adiantada por um cliente
 * específico é valor a reembolsar, que já tem lugar em {@code /clients/{id}/payments}.
 *
 * <p>Restrito a ADMIN, como todo o financeiro.
 */
@Tag(name = "Financeiro — despesas", description = "Saídas de caixa do escritório")
@SecurityRequirements({
    @SecurityRequirement(name = "bearerAuth"),
    @SecurityRequirement(name = "tenantHeader")
})
@RestController
@RequestMapping("/api/v1/expenses")
@RequerAdmin
public class OfficeExpenseController {

    private final OfficeExpenseService service;

    public OfficeExpenseController(OfficeExpenseService service) {
        this.service = service;
    }

    @Operation(
            summary = "Lançar despesa",
            description =
                    "Nasce Pendente quando `status` é omitido. Informar `paidDate` marca como paga;"
                            + " marcar como paga sem data assume hoje.")
    @PostMapping
    public ResponseEntity<ApiResponse<OfficeExpenseResponseDTO>> create(
            @Valid @RequestBody OfficeExpenseRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.successObject(service.create(dto)));
    }

    @Operation(
            summary = "Listar despesas",
            description =
                    """
                    Paginada. Ordena vencido, a vencer, cancelado e pago — nessa ordem, e \
                    dentro de cada faixa por vencimento. `dueFrom`/`dueTo` e `paidFrom`/`paidTo` \
                    são mutuamente exclusivos — mandar os dois responde 400.

                    Traz a projeção da grade; o detalhe de um lançamento vem em \
                    `GET /api/v1/expenses/{id}`.""")
    @GetMapping
    public ResponseEntity<ApiResponse<OfficeExpenseListItemDTO>> list(
            @ParameterObject OfficeExpenseSearchParams params) {
        var page = service.list(params);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de uma despesa")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OfficeExpenseResponseDTO>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(id)));
    }

    @Operation(
            summary = "Substituir despesa",
            description = "PUT substitui a despesa inteira: campo omitido é apagado.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OfficeExpenseResponseDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody OfficeExpenseRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.successObject(service.update(id, dto)));
    }

    @Operation(
            summary = "Excluir despesa",
            description =
                    "Exclusão lógica. Resultado de mês fechado não muda porque alguém errou o"
                            + " lançamento — some da lista, permanece na trilha.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Totais do período",
            description =
                    """
                    Três baldes disjuntos (vencido, a vencer, pago; Cancelado fora), mais \
                    `byCategory` — a composição do que foi **pago**, que é o que alimenta o \
                    gráfico de gasto. Incluir o não pago mudaria a pergunta para "no que \
                    pretendemos gastar".""")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FinanceSummaryDTO>> summary(
            @ParameterObject OfficeExpenseSearchParams params) {
        return ResponseEntity.ok(ApiResponse.successObject(service.summary(params)));
    }

    @Operation(summary = "Série mensal para o gráfico")
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
        return ResponseEntity.ok(
                ApiResponse.successList(
                        service.timeline(from, to, Serie.porPagamento("basis", basis))));
    }
}
