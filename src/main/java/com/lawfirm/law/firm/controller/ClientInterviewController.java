package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientInterviewRequestDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.service.ClientInterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Tag(name = "Entrevistas",
        description = "Entrevistas/atendimentos do cliente: data, duração e conteúdo rich text")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/interviews")
public class ClientInterviewController {

    private final ClientInterviewService service;

    public ClientInterviewController(ClientInterviewService service) {
        this.service = service;
    }

    @Operation(summary = "Registrar entrevista")
    @PostMapping
    public ResponseEntity<ApiResponse<ClientInterviewResponseDTO>> create(
            @PathVariable UUID clientId, @Valid @RequestBody ClientInterviewRequestDTO dto) {
        ClientInterviewResponseDTO created = service.create(clientId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successObject(created));
    }

    @Operation(summary = "Listar entrevistas",
            description = "Entrevistas ativas do cliente, mais recente primeiro, paginadas no envelope padrão.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientInterviewResponseDTO>> list(
            @PathVariable UUID clientId,
            @Parameter(description = "Número da página (1-based)") @RequestParam(defaultValue = "1") int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10") int pageSize) {
        Page<ClientInterviewResponseDTO> page = service.list(clientId, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de uma entrevista")
    @GetMapping("/{interviewId}")
    public ResponseEntity<ApiResponse<ClientInterviewResponseDTO>> get(
            @PathVariable UUID clientId, @PathVariable UUID interviewId) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(clientId, interviewId)));
    }

    @Operation(summary = "Atualizar entrevista",
            description = "Substitui o conteúdo; data e duração só mudam se enviadas.")
    @PutMapping("/{interviewId}")
    public ResponseEntity<ApiResponse<ClientInterviewResponseDTO>> update(
            @PathVariable UUID clientId, @PathVariable UUID interviewId,
            @Valid @RequestBody ClientInterviewRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.successObject(service.update(clientId, interviewId, dto)));
    }

    @Operation(summary = "Excluir entrevista", description = "Soft delete - preservada para eventual auditoria.")
    @DeleteMapping("/{interviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID interviewId) {
        service.delete(clientId, interviewId);
        return ResponseEntity.noContent().build();
    }
}
