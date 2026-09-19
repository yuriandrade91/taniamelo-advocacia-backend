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
                        + "contribuição e número do benefício. A senha do INSS pode ser ESCRITA "
                        + "aqui, mas não volta na resposta - para lê-la, "
                        + "GET /clients/{clientId}/inss-password, restrito e auditado.")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/professional-data")
public class ClientProfessionalDataController {

    private final ClientService clientService;

    public ClientProfessionalDataController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(
            summary = "Dados profissionais do cliente",
            description =
                    "Aba 'Dados profissionais': profissão, NIT/PIS, CTPS, tempo de contribuição "
                            + "(anos/meses/dias, com o total em meses e a frase de exibição "
                            + "derivados no servidor) e número do benefício. NÃO devolve a senha "
                            + "do INSS.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientProfessionalDataResponseDTO>> get(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                ApiResponse.successObject(clientService.getProfessionalData(clientId)));
    }

    @Operation(
            summary = "Atualizar dados profissionais",
            description =
                    "Substituição completa só do subconjunto de dados profissionais. "
                            + "`inssPassword` ausente ou em branco significa 'mantém a que está "
                            + "gravada' - como ela não volta em resposta nenhuma, quem edita a aba "
                            + "não a tem em mãos para devolver.")
    @PutMapping
    public ResponseEntity<ApiResponse<ClientProfessionalDataResponseDTO>> update(
            @PathVariable UUID clientId, @Valid @RequestBody ClientProfessionalDataRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(clientService.updateProfessionalData(clientId, dto)));
    }
}
