package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueRequestDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueResponseDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueSearchParams;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.security.RequerAdmin;
import com.lawfirm.law.firm.service.OfficeRevenueService;
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
 * Receita do escritório — honorário, parecer avulso, reembolso. É a metade das entradas da
 * Carteira.
 *
 * <p><b>Não confundir com {@code /api/v1/payments}</b>, que é o que o INSS paga ao cliente e não
 * passa pelo caixa daqui. As duas coisas têm quase o mesmo formato, e é por isso que moram em
 * tabelas separadas: com uma coluna de tipo, o primeiro lançamento errado contaminaria os dois
 * relatórios ao mesmo tempo, sem consulta capaz de separar depois.
 *
 * <p>O cliente é opcional, e {@code sourcePaymentId} aponta a parcela do INSS que originou o
 * honorário de êxito — é o que permite conferir "30% dos atrasados" contra o valor de fato, meses
 * depois.
 *
 * <p>Restrito a ADMIN, como todo o financeiro.
 */
@Tag(name = "Financeiro — receitas", description = "Entradas de caixa do escritório")
@SecurityRequirements({
    @SecurityRequirement(name = "bearerAuth"),
    @SecurityRequirement(name = "tenantHeader")
})
@RestController
@RequestMapping("/api/v1/revenues")
@RequerAdmin
public class OfficeRevenueController {

    private final OfficeRevenueService service;

    public OfficeRevenueController(OfficeRevenueService service) {
        this.service = service;
    }

    @Operation(
            summary = "Lançar receita",
            description =
                    "Nasce Pendente quando `status` é omitido. Informar `paidDate` marca como"
                            + " recebida; marcar como recebida sem data assume hoje.")
    @PostMapping
    public ResponseEntity<ApiResponse<OfficeRevenueResponseDTO>> create(
            @Valid @RequestBody OfficeRevenueRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.successObject(service.create(dto)));
    }

    @Operation(
            summary = "Listar receitas",
            description =
                    """
                    Paginada, por vencimento. `dueFrom`/`dueTo` e `paidFrom`/`paidTo` são \
                    mutuamente exclusivos — mandar os dois responde 400.""")
    @GetMapping
    public ResponseEntity<ApiResponse<OfficeRevenueResponseDTO>> list(
            @ParameterObject OfficeRevenueSearchParams params) {
        var page = service.list(params);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de uma receita")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OfficeRevenueResponseDTO>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(id)));
    }

    @Operation(
            summary = "Substituir receita",
            description = "PUT substitui a receita inteira: campo omitido é apagado.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OfficeRevenueResponseDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody OfficeRevenueRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.successObject(service.update(id, dto)));
    }

    @Operation(
            summary = "Excluir receita",
            description =
                    "Exclusão lógica: some da lista e permanece na trilha. Excluir o cliente"
                            + " também não apaga a receita já apurada — o dinheiro entrou.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Totais do período",
            description =
                    "Três baldes disjuntos (vencido, a vencer, recebido; Cancelado fora dos três e"
                            + " do total).")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FinanceSummaryDTO>> summary(
            @ParameterObject OfficeRevenueSearchParams params) {
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
