package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientPaymentRequestDTO;
import com.lawfirm.law.firm.dto.ClientPaymentResponseDTO;
import com.lawfirm.law.firm.dto.ClientPaymentUpdateRequestDTO;
import com.lawfirm.law.firm.service.ClientPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;


@Tag(name = "Financeiro do cliente", description = "Parcelas de honorários cobradas do cliente")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/payments")
public class ClientPaymentController {

    private final ClientPaymentService service;

    public ClientPaymentController(ClientPaymentService service) {
        this.service = service;
    }

    @Operation(summary = "Lançar parcela de honorários", description = "Toda parcela nasce com status Pendente.")
    @PostMapping
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> create(
            @PathVariable UUID clientId, @Valid @RequestBody ClientPaymentRequestDTO dto) {
        ClientPaymentResponseDTO created = service.create(clientId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successObject(created));
    }

    @Operation(summary = "Listar parcelas do cliente",
            description = "Ordenadas por vencimento, paginadas no envelope padrão; inclui 'overdue' calculado em runtime.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> list(
            @PathVariable UUID clientId,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        var page = service.list(clientId, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), com.lawfirm.law.firm.dto.Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de uma parcela")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> get(
            @PathVariable UUID clientId, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(clientId, paymentId)));
    }

    @Operation(summary = "Atualizar parcela",
            description = "PATCH parcial. Marcar status=Pago sem informar paidDate assume a data de hoje; " +
                    "mudar para Pendente/Cancelado limpa a data de pagamento (a menos que uma nova seja enviada).")
    @PatchMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<ClientPaymentResponseDTO>> update(
            @PathVariable UUID clientId, @PathVariable UUID paymentId, @RequestBody ClientPaymentUpdateRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.successObject(service.update(clientId, paymentId, dto)));
    }

    @Operation(summary = "Excluir parcela", description = "Soft delete - preservada para auditoria financeira.")
    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID paymentId) {
        service.delete(clientId, paymentId);
        return ResponseEntity.noContent().build();
    }
}
