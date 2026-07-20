package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cliente - Situação", description = "Histórico de mudanças de situação do cliente")
@RestController
@RequestMapping("/api/v1/clients/{id}/situation-history")
public class ClientSituationHistoryController {

    private final ClientService clientService;

    public ClientSituationHistoryController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(
            summary = "Histórico de mudanças de situação",
            description =
                    "Lista paginada no envelope padrão, mais recente primeiro, com o usuário que fez cada mudança.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientSituationHistoryDTO>> list(
            @PathVariable UUID id,
            @Parameter(description = "Número da página (1-based)") @RequestParam(defaultValue = "1")
                    int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10")
                    int pageSize) {
        Page<ClientSituationHistoryDTO> page =
                clientService.historyByClientId(id, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }
}
