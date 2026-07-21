package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPatchResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.dto.NotBillableRequestDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.service.ClientPatchOutcome;
import com.lawfirm.law.firm.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Cliente(s)",
        description = "Cadastro e acompanhamento de clientes do escritório previdenciário")
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @Operation(
            summary = "Cadastrar cliente",
            description =
                    "Cria um novo cliente. Identificadores únicos (CPF, NIT/PIS, número do benefício) são "
                            + "validados contra duplicidade antes de gravar. Endereços, arquivos, entrevistas e pagamentos "
                            + "são cadastrados nos sub-recursos de /clients/{id} após a criação.")
    @PostMapping
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> create(
            @Valid @RequestBody ClientCreateRequestDTO createDto, UriComponentsBuilder uriBuilder) {
        ClientDetailsDTO created = clientService.create(createDto);
        URI location =
                uriBuilder.path("/api/v1/clients/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(ApiResponse.successObject(created));
    }

    @Operation(
            summary = "Listar clientes",
            description =
                    "Lista paginada no envelope padrão, ordenada pela atividade mais recente (updatedAt desc). "
                            + "Filtros opcionais: texto livre (nome/CPF, sem acento), tipos de benefício, situações e "
                            + "intervalo de criação (datas ISO-8601: yyyy-MM-dd ou timestamp completo).")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientListResponseDTO>> list(
            @Parameter(description = "Número da página (1-based)") @RequestParam(defaultValue = "1")
                    int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10")
                    int pageSize,
            @Parameter(description = "Busca livre por nome completo ou CPF")
                    @RequestParam(required = false)
                    String searchTerm,
            @Parameter(description = "Filtra por um ou mais tipos de benefício (nome ou label)")
                    @RequestParam(required = false)
                    List<BenefitType> benefitType,
            @Parameter(description = "Filtra por uma ou mais situações (nome ou label)")
                    @RequestParam(required = false)
                    List<Situation> situation,
            @Parameter(description = "Criado a partir de (ISO-8601: yyyy-MM-dd ou timestamp)")
                    @RequestParam(required = false)
                    String createdFrom,
            @Parameter(description = "Criado até (ISO-8601: yyyy-MM-dd ou timestamp)")
                    @RequestParam(required = false)
                    String createdTo) {

        Page<ClientListResponseDTO> page =
                clientService.listSummary(
                        pageNumber,
                        pageSize,
                        searchTerm,
                        benefitType,
                        situation,
                        parseInstant("createdFrom", createdFrom, true),
                        parseInstant("createdTo", createdTo, false));

        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(
            summary = "Buscar cliente por id",
            description =
                    "Dados completos do cliente, incluindo a senha do INSS (descriptografada para o usuário autenticado).")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getById(@PathVariable UUID id) {
        ClientDetailsDTO dto =
                clientService.findById(id).orElseThrow(() -> NotFoundException.of("Cliente", id));
        return ResponseEntity.ok(ApiResponse.successObject(dto));
    }

    @Operation(
            summary = "Histórico de mudanças de situação",
            description =
                    "Lista paginada no envelope padrão, mais recente primeiro, com o usuário que fez cada mudança. "
                            + "Toda mudança de situação feita via PATCH /clients/{id} gera um registro aqui automaticamente.")
    @GetMapping("/{id}/situation-history")
    public ResponseEntity<ApiResponse<ClientSituationHistoryDTO>> situationHistory(
            @PathVariable UUID id,
            @Parameter(description = "Número da página (1-based)") @RequestParam(defaultValue = "1")
                    int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10")
                    int pageSize) {
        Page<ClientSituationHistoryDTO> page =
                clientService.historyByClientId(id, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(
            summary = "Atualizar cliente (substituição completa)",
            description =
                    "PUT = substituição total dos campos editáveis - envie o objeto completo. "
                            + "Para atualização parcial (situação/arrecadação) use PATCH /clients/{id}.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody ClientUpdateRequestDTO body) {
        return ResponseEntity.ok(ApiResponse.successObject(clientService.update(id, body)));
    }

    @Operation(
            summary = "Atualização parcial (situação, benefício e/ou arrecadação)",
            description =
                    """
                    PATCH parcial: envie só o que quer mudar (`situation`, `benefit` e/ou \
                    `notBillable`). Mudança de situação gera automaticamente um registro no \
                    histórico (`GET /clients/{id}/situation-history`); mudança de benefício e de \
                    arrecadação não.

                    **Formato de `situation` e `benefit`:** aceita o nome da constante (ex.: \
                    `APOSENTADORIA_RURAL`) ou o label em PT-BR (ex.: `Aposentadoria rural`), sem \
                    diferenciar maiúsculas/minúsculas nem acentuação. Qualquer outro valor retorna \
                    400.

                    **Valores válidos de `situation`:**
                    - `Formulário preenchido`
                    - `Análise documental`
                    - `Planejamento em execução`
                    - `Planejamento concluído`
                    - `Benefício futuro`
                    - `Benefício concluído`

                    **Valores válidos de `benefit`:**
                    - `Aposentadoria por idade`
                    - `Aposentadoria por tempo de contribuição`
                    - `Aposentadoria por incapacidade permanente`
                    - `Aposentadoria especial`
                    - `Aposentadoria por deficiência`
                    - `Aposentadoria por tempo de contribuição do professor`
                    - `Aposentadoria por invalidez`
                    - `Aposentadoria rural`
                    - `Aposentadoria para PCD`""")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> patch(
            @PathVariable UUID id, @RequestBody ClientPatchRequestDTO patch) {
        ClientPatchOutcome outcome = clientService.patch(id, patch);
        return ResponseEntity.ok(
                ApiResponse.successObject(new ClientPatchResponseDTO(patchMessage(outcome))));
    }

    @Operation(
            summary = "Atualizar arrecadação (não cobrável)",
            description =
                    "PATCH de propósito único para alternar a flag 'não cobrável', sem tocar em situação "
                            + "nem gerar histórico.")
    @PatchMapping("/{id}/not-billable")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> patchNotBillable(
            @PathVariable UUID id, @Valid @RequestBody NotBillableRequestDTO body) {
        ClientPatchRequestDTO patch = new ClientPatchRequestDTO();
        patch.setNotBillable(body.getNotBillable());
        clientService.patch(id, patch);

        String message =
                Boolean.TRUE.equals(body.getNotBillable())
                        ? "Cliente marcado como não cobrável."
                        : "Cliente marcado como cobrável.";
        return ResponseEntity.ok(ApiResponse.successObject(new ClientPatchResponseDTO(message)));
    }

    @Operation(
            summary = "Excluir cliente",
            description =
                    "Remove o cliente e, em cascata, seus sub-recursos (histórico, endereços, arquivos, "
                            + "entrevistas, pagamentos).")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.successObject(
                        new ClientPatchResponseDTO("Cliente excluído com sucesso.")));
    }

    // ── Private helpers ──

    /** Monta a mensagem do PATCH a partir de quais campos efetivamente mudaram. */
    private static String patchMessage(ClientPatchOutcome outcome) {
        List<String> changed = new ArrayList<>();
        if (outcome.situationChanged()) changed.add("Situação");
        if (outcome.benefitChanged()) changed.add("Benefício");
        if (outcome.notBillableChanged()) changed.add("Arrecadação");

        if (changed.isEmpty()) {
            return "Nenhuma alteração realizada!";
        }
        String joined =
                changed.size() == 1
                        ? changed.get(0)
                        : String.join(", ", changed.subList(0, changed.size() - 1))
                                + " e "
                                + changed.get(changed.size() - 1);
        return joined + " atualizado(s) com sucesso!";
    }

    /**
     * Aceita ISO-8601: data (yyyy-MM-dd) ou timestamp completo. Valor inválido gera 400 explícito -
     * nunca é ignorado silenciosamente.
     */
    private static Instant parseInstant(String field, String value, boolean startOfDay) {
        if (value == null || value.isBlank()) return null;
        String s = value.trim();
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            // tenta como data simples abaixo
        }
        try {
            LocalDate date = LocalDate.parse(s);
            return startOfDay
                    ? date.atStartOfDay(ZoneOffset.UTC).toInstant()
                    : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        } catch (DateTimeParseException ignored) {
            throw new ValidationException(
                    field,
                    ValidationErrorCode.INVALID_DATE,
                    "Data inválida (use ISO-8601, ex.: 2026-07-18 ou 2026-07-18T00:00:00Z): "
                            + value);
        }
    }
}
