package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPatchResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.service.ClientPatchOutcome;
import com.lawfirm.law.firm.service.ClientService;
import com.lawfirm.law.firm.util.RequestDates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
                    """
                    Cria um novo cliente. Identificadores únicos (CPF, NIT/PIS, número do benefício) \
                    são validados contra duplicidade antes de gravar. Endereços, arquivos, entrevistas \
                    e pagamentos são cadastrados nos sub-recursos de /clients/{id} após a criação.

                    **Valores válidos de `clientType`:** `Verificado` ou `Potencial` (nome do enum ou \
                    label, case/acento-insensitive). Qualquer outro valor retorna 400.""")
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
                    """
                    Lista paginada no envelope padrão, ordenada pela atividade mais recente (updatedAt desc). \
                    Filtros opcionais: texto livre (nome/CPF, sem acento), tipos de benefício, situações e \
                    intervalo de criação (datas ISO-8601: yyyy-MM-dd ou timestamp completo).

                    `benefitType` e `situation` aceitam um ou mais valores (repita o parâmetro na query \
                    string), cada um pelo nome do enum ou pelo label (case/acento-insensitive). Valor \
                    desconhecido retorna 400 (`INVALID_ENUM_VALUE`).

                    **Valores válidos de `benefitType`:**
                    - `Aposentadoria por idade`
                    - `Aposentadoria por tempo de contribuição`
                    - `Aposentadoria por incapacidade permanente`
                    - `Aposentadoria especial`
                    - `Aposentadoria por deficiência`
                    - `Aposentadoria por tempo de contribuição do professor`
                    - `Aposentadoria por invalidez`
                    - `Aposentadoria rural`
                    - `Aposentadoria para PCD`

                    **Valores válidos de `situation`:**
                    - `Formulário preenchido`
                    - `Análise documental`
                    - `Planejamento em execução`
                    - `Planejamento concluído`
                    - `Benefício futuro`
                    - `Benefício concluído`""")
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
                    List<String> benefitType,
            @Parameter(description = "Filtra por uma ou mais situações (nome ou label)")
                    @RequestParam(required = false)
                    List<String> situation,
            @Parameter(
                            description =
                                    "Filtra por um ou mais tipos de cliente: VERIFICADO / POTENCIAL"
                                            + " (aceita o nome da constante ou o label"
                                            + " \"Verificado\"/\"Potencial\"). Repita o parâmetro"
                                            + " para múltiplos valores.")
                    @RequestParam(required = false)
                    List<String> clientType,
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
                        parseEnumList("benefitType", benefitType, BenefitType::fromLabel),
                        parseEnumList("situation", situation, Situation::fromLabel),
                        parseEnumList("clientType", clientType, ClientType::fromLabel),
                        RequestDates.parseInstant("createdFrom", createdFrom, true),
                        RequestDates.parseInstant("createdTo", createdTo, false));

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
                    """
                    Lista paginada no envelope padrão, mais recente primeiro. Toda mudança de \
                    situação via `PATCH /clients/{id}` gera um registro aqui automaticamente.

                    Cada entrada traz `previousSituation` e `currentSituation`, para a linha do \
                    tempo dizer "de X para Y". Na primeira entrada (situação definida no cadastro) \
                    `previousSituation` vem nulo.

                    `changedByUserId` é o id do usuário; o nome sai de `GET /users`.""")
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
                    """
                    PUT = substituição total dos campos editáveis - envie o objeto completo. Para \
                    atualização parcial (situação/benefício/arrecadação) use PATCH /clients/{id}.

                    **Valores válidos de `clientType`:** `Verificado` ou `Potencial` (nome do enum ou \
                    label, case/acento-insensitive). Qualquer outro valor retorna 400.""")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody ClientUpdateRequestDTO body) {
        return ResponseEntity.ok(ApiResponse.successObject(clientService.update(id, body)));
    }

    @Operation(
            summary = "Atualização parcial (situação, benefício, tipo de cliente e/ou arrecadação)",
            description =
                    """
                    PATCH parcial: envie só o que quer mudar (`situation`, `benefit`, `clientType` \
                    e/ou `notBillable`). Mudança de situação gera automaticamente um registro no \
                    histórico (`GET /clients/{id}/situation-history`); mudança de benefício, tipo \
                    de cliente e de arrecadação não.

                    **Formato de `situation`, `benefit` e `clientType`:** aceita o nome da \
                    constante (ex.: `APOSENTADORIA_RURAL`) ou o label em PT-BR (ex.: \
                    `Aposentadoria rural`), sem diferenciar maiúsculas/minúsculas nem acentuação. \
                    Qualquer outro valor retorna 400.

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
                    - `Aposentadoria para PCD`

                    **Valores válidos de `clientType`:**
                    - `Verificado`
                    - `Potencial`""")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> patch(
            @PathVariable UUID id, @RequestBody ClientPatchRequestDTO patch) {
        ClientPatchOutcome outcome = clientService.patch(id, patch);
        return ResponseEntity.ok(
                ApiResponse.successObject(new ClientPatchResponseDTO(patchMessage(outcome))));
    }

    @Operation(
            summary = "Excluir cliente",
            description =
                    """
                    Exclusão **lógica**: o cliente sai de todas as listagens e buscas, mas a ficha \
                    continua no banco.

                    Nada em cascata é apagado - endereços, entrevistas, arquivos, pagamentos e o \
                    histórico de situação permanecem, e voltam inteiros por \
                    `PATCH /clients/{id}/restore`.

                    Compromissos vinculados na agenda mantêm o nome do cliente (gravado junto do \
                    compromisso), então nenhum item da agenda fica órfão.

                    Buscar, editar ou listar sub-recursos de um cliente excluído devolve 404 - do \
                    ponto de vista da API ele não existe mais.""")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.successObject(
                        new ClientPatchResponseDTO("Cliente excluído com sucesso.")));
    }

    @Operation(
            summary = "Restaurar cliente excluído",
            description =
                    """
                    Desfaz a exclusão lógica: o cliente volta às listagens com toda a ficha \
                    (endereços, entrevistas, arquivos, pagamentos e histórico), que nunca foi \
                    apagada.

                    Idempotente: restaurar um cliente que já está ativo devolve 200 sem alterar \
                    nada. Id inexistente devolve 404.""")
    @PatchMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<ClientPatchResponseDTO>> restore(@PathVariable UUID id) {
        clientService.restore(id);
        return ResponseEntity.ok(
                ApiResponse.successObject(
                        new ClientPatchResponseDTO("Cliente restaurado com sucesso.")));
    }

    // ── Private helpers ──

    /**
     * Monta a mensagem do PATCH a partir de quais campos efetivamente mudaram, com concordância de
     * gênero/número correta (singular casado com o gênero do campo; plural feminino só quando todos
     * os campos são femininos, senão masculino - regra padrão do PT-BR para listas mistas).
     */
    private static String patchMessage(ClientPatchOutcome outcome) {
        List<String> names = new ArrayList<>();
        List<Boolean> feminine = new ArrayList<>();
        if (outcome.situationChanged()) {
            names.add("Situação");
            feminine.add(true);
        }
        if (outcome.benefitChanged()) {
            names.add("Benefício");
            feminine.add(false);
        }
        if (outcome.clientTypeChanged()) {
            names.add("Tipo de cliente");
            feminine.add(false);
        }
        if (outcome.notBillableChanged()) {
            names.add("Arrecadação");
            feminine.add(true);
        }

        if (names.isEmpty()) {
            return "Nenhuma alteração realizada!";
        }
        String joined =
                names.size() == 1
                        ? names.get(0)
                        : String.join(", ", names.subList(0, names.size() - 1))
                                + " e "
                                + names.get(names.size() - 1);
        boolean allFeminine = !feminine.contains(false);
        String participle =
                names.size() == 1
                        ? (feminine.get(0) ? "atualizada" : "atualizado")
                        : (allFeminine ? "atualizadas" : "atualizados");
        return joined + " " + participle + " com sucesso!";
    }

    /**
     * Converte os valores de um filtro repetido (nome do enum ou label) para o enum de destino. Não
     * delega ao conversor de query params do Spring: List&lt;Enum&gt; com um único valor não passa
     * pelo ConversionService customizado (cai no StringToEnumConverterFactory padrão), então a
     * validação é feita aqui, com mensagem clara em vez de 400 genérico.
     */
    private static <E extends Enum<E>> List<E> parseEnumList(
            String field, List<String> rawValues, Function<String, E> fromLabel) {
        if (rawValues == null || rawValues.isEmpty()) return null;
        List<E> result = new ArrayList<>();
        for (String raw : rawValues) {
            try {
                result.add(fromLabel.apply(raw));
            } catch (IllegalArgumentException ex) {
                throw new ValidationException(
                        field, ValidationErrorCode.INVALID_ENUM_VALUE, ex.getMessage());
            }
        }
        return result;
    }
}
