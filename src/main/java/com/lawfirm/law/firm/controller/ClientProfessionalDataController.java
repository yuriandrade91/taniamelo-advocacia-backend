package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
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
        name = "Cliente - Dados Profissionais",
        description =
                "Aba 'Dados profissionais' do cliente: profissão, NIT/PIS, CTPS, tempo de "
                        + "contribuição, benefício e senha do INSS")
@RestController
@RequestMapping("/api/v1/clients/{id}/professional-data")
public class ClientProfessionalDataController {

    private final ClientService clientService;

    public ClientProfessionalDataController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(
            summary = "Dados profissionais do cliente",
            description =
                    "Aba 'Dados profissionais': profissão, NIT/PIS, CTPS, tempo de contribuição (com total em "
                            + "meses derivado no servidor), número do benefício e senha do INSS.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientProfessionalDataResponseDTO>> get(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.successObject(clientService.getProfessionalData(id)));
    }

    @Operation(
            summary = "Atualizar dados profissionais",
            description = "Substituição completa só do subconjunto de dados profissionais.")
    @PutMapping
    public ResponseEntity<ApiResponse<ClientProfessionalDataResponseDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody ClientProfessionalDataRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(clientService.updateProfessionalData(id, dto)));
    }
}
