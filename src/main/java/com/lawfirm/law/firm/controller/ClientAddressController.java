package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientAddressBatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientAddressRequestDTO;
import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.service.ClientAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Cliente - Endereço(s)",
        description =
                "Endereços residencial/comercial/correspondência do cliente (um marcado como principal)")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/addresses")
public class ClientAddressController {

    private final ClientAddressService service;

    public ClientAddressController(ClientAddressService service) {
        this.service = service;
    }

    @Operation(
            summary = "Cadastrar endereço",
            description =
                    "O primeiro endereço do cliente vira principal automaticamente. Enviar isPrimary=true "
                            + "desmarca o principal anterior.")
    @PostMapping
    public ResponseEntity<ApiResponse<ClientAddressResponseDTO>> create(
            @PathVariable UUID clientId, @Valid @RequestBody ClientAddressRequestDTO dto) {
        ClientAddressResponseDTO created = service.create(clientId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successObject(created));
    }

    @Operation(
            summary = "Cadastrar vários endereços de uma vez",
            description =
                    """
                    Grava a lista inteira em **uma transação**: se qualquer endereço falhar, \
                    nenhum é gravado.

                    É o caminho do cadastro, que permite mais de um endereço. Um POST por endereço \
                    também funciona, mas sem transação comum - uma falha no segundo deixaria a \
                    ficha pela metade.

                    A regra do principal é a mesma do POST individual: o primeiro endereço do \
                    cliente vira principal, e `isPrimary=true` desmarca o anterior. Se mais de um \
                    item da lista pedir principal, vale o **último** - a ordem da lista decide.

                    Aceita de 1 a 10 endereços. Devolve os criados, na ordem enviada.""")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<ClientAddressResponseDTO>> createBatch(
            @PathVariable UUID clientId, @Valid @RequestBody ClientAddressBatchRequestDTO dto) {
        List<ClientAddressResponseDTO> created = service.createAll(clientId, dto.getAddresses());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successList(created));
    }

    @Operation(
            summary = "Listar endereços do cliente",
            description =
                    "Principal primeiro, depois por ordem de cadastro. Paginado no envelope padrão.")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientAddressResponseDTO>> list(
            @PathVariable UUID clientId,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        var page = service.list(clientId, pageNumber, pageSize);
        return ResponseEntity.ok(
                ApiResponse.successList(
                        page.getContent(), com.lawfirm.law.firm.dto.Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de um endereço")
    @GetMapping("/{addressId}")
    public ResponseEntity<ApiResponse<ClientAddressResponseDTO>> get(
            @PathVariable UUID clientId, @PathVariable UUID addressId) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(clientId, addressId)));
    }

    @Operation(
            summary = "Atualizar endereço (substituição completa)",
            description =
                    "Se for o único endereço do cliente, continua sendo o principal independente do valor enviado.")
    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<ClientAddressResponseDTO>> update(
            @PathVariable UUID clientId,
            @PathVariable UUID addressId,
            @Valid @RequestBody ClientAddressRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.update(clientId, addressId, dto)));
    }

    @Operation(
            summary = "Excluir endereço",
            description =
                    "Se o endereço excluído era o principal e restarem outros, o mais antigo vira o novo principal automaticamente.")
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID addressId) {
        service.delete(clientId, addressId);
        return ResponseEntity.noContent().build();
    }
}
