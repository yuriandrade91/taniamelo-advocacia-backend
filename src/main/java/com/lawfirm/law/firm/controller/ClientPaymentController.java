package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientPaymentRequestDTO;
import com.lawfirm.law.firm.dto.ClientPaymentResponseDTO;
import com.lawfirm.law.firm.dto.ClientPaymentUpdateRequestDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.security.RequerAdmin;
import com.lawfirm.law.firm.service.ClientPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Financeiro do cliente - <b>o recurso inteiro é ADMIN</b>.
 *
 * <p>A anotação está na classe, e não método a método, porque a regra é do recurso: qualquer rota
 * nova aqui já nasce fechada. O corte é diferente do resto da API (onde STAFF opera e ADMIN/LAWYER
 * destrói) porque aqui não se trata de risco de perder dado, e sim de quem tem que ver honorários.
 */
@Tag(
        name = "Cliente - Financeiro",
        description = "Parcelas de honorários cobradas do cliente. Acesso restrito a ADMIN.")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/payments")
@RequerAdmin
public class ClientPaymentController {

    private final ClientPaymentService service;

    public ClientPaymentController(ClientPaymentService service) {
        this.service = service;
    }

    @Operation(
            summary = "Lançar parcela de honorários",
            description = "Toda parcela nasce com status Pendente.")
    @PostMapping
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> create(
            @PathVariable UUID clientId, @Valid @RequestBody ClientPaymentRequestDTO dto) {
        ClientPaymentResponseDTO created = service.create(clientId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successObject(created));
    }

    @Operation(
            summary = "Listar parcelas do cliente",
            description =
                    """
                    Mesmos filtros de `GET /api/v1/payments` — período por vencimento                     (`dueFrom`/`dueTo`) **ou** por pagamento (`paidFrom`/`paidTo`, nunca os dois),                     `status`, `paymentMethod` e `searchTerm` — só que recortados neste cliente.

                    Ordenadas por vencimento, paginadas no envelope padrão, com `overdue`                     calculado na leitura. O `clientId` sai da URL: mandá-lo também na query com                     outro valor responde 400 em vez de escolher um dos dois.""")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> list(
            @PathVariable UUID clientId, @ParameterObject PaymentSearchParams params) {
        var page = service.list(clientId, params);
        return ResponseEntity.ok(
                ApiResponse.successList(
                        page.getContent(), com.lawfirm.law.firm.dto.Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de uma parcela")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> get(
            @PathVariable UUID clientId, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(clientId, paymentId)));
    }

    @Operation(
            summary = "Atualizar parcela",
            description =
                    "PATCH parcial. Marcar status=Pago sem informar paidDate assume a data de hoje; "
                            + "mudar para Pendente/Cancelado limpa a data de pagamento (a menos que uma nova seja enviada).")
    @PatchMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> update(
            @PathVariable UUID clientId,
            @PathVariable UUID paymentId,
            @RequestBody ClientPaymentUpdateRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.update(clientId, paymentId, dto)));
    }

    @Operation(
            summary = "Excluir parcela",
            description = "Soft delete - preservada para auditoria financeira.")
    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID paymentId) {
        service.delete(clientId, paymentId);
        return ResponseEntity.noContent().build();
    }
}
