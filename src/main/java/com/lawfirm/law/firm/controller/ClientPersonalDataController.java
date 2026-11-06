package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientPersonalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Cliente - Dados Pessoais",
        description = "Aba 'Dados pessoais' do cliente: identidade e contato")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/personal-data")
public class ClientPersonalDataController {

    private final ClientService clientService;

    public ClientPersonalDataController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(
            summary = "Dados pessoais do cliente",
            description =
                    "Aba 'Dados pessoais': identidade e contato. Dados profissionais e endereços têm endpoints próprios.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientPersonalDataResponseDTO>> get(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                ApiResponse.successObject(clientService.getPersonalData(clientId)));
    }

    @Operation(
            summary = "Atualizar dados pessoais",
            description =
                    "Substituição completa só do subconjunto de dados pessoais - não altera dados "
                            + "profissionais, endereços, benefício, situação ou arrecadação.")
    @PutMapping
    public ResponseEntity<ApiResponse<ClientPersonalDataResponseDTO>> update(
            @PathVariable UUID clientId, @Valid @RequestBody ClientPersonalDataRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(clientService.updatePersonalData(clientId, dto)));
    }
}
