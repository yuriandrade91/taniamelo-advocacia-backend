package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.AppointmentCancelRequestDTO;
import com.lawfirm.law.firm.dto.AppointmentHistoryDTO;
import com.lawfirm.law.firm.dto.AppointmentRequestDTO;
import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.AppointmentSearchParams;
import com.lawfirm.law.firm.dto.AppointmentSummaryDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Agenda do escritório: CRUD de compromissos (top-level, tenant-scoped) + resumo por mês (abas) e
 * trilha de alterações. Envelope, paginação (1-based) e resolução de tenant no padrão do projeto.
 *
 * <p>Segurança declarada com os DOIS esquemas (bearer + tenant): uma {@code @SecurityRequirement}
 * por operação SUBSTITUI o requisito global do springdoc, então precisamos repetir o bearer aqui -
 * senão o Swagger deixa de anexar o token e o "Try it out" volta 401.
 */
@Tag(
        name = "Agenda",
        description =
                "Compromissos do escritório: entrevistas, reuniões, perícias, audiências e prazos")
@SecurityRequirements({
    @SecurityRequirement(name = "bearerAuth"),
    @SecurityRequirement(name = "tenantHeader")
})
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @Operation(summary = "Criar compromisso", description = "Nasce com status Agendado.")
    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> create(
            @Valid @RequestBody AppointmentRequestDTO dto) {
        AppointmentResponseDTO created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successObject(created));
    }

    @Operation(
            summary = "Listar compromissos",
            description =
                    "Paginado no envelope padrão, ordenado por início (asc). Filtros opcionais:"
                            + " ano+mês (abas, cada um aceita múltiplos valores - repita o parâmetro na"
                            + " query string, ex.: year=2025&year=2026), intervalo (from/to ISO-8601,"
                            + " usado só quando nenhum ano/mês é informado), tipo e situação (também"
                            + " aceitam múltiplos valores), cliente e busca por título.")
    @GetMapping
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> list(
            @ParameterObject AppointmentSearchParams params) {
        Page<AppointmentResponseDTO> page = service.list(params);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(
            summary = "Resumo por mês",
            description =
                    "Contagem de compromissos PENDENTES (status Agendado) por mês do ano - alimenta"
                            + " as abas da agenda. Toda ação reflete no número: criar soma; concluir,"
                            + " cancelar ou excluir subtrai.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AppointmentSummaryDTO>> summary(
            @Parameter(description = "Ano de referência") @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.successList(service.summary(year)));
    }

    @Operation(
            summary = "Conflitos de horário",
            description =
                    """
                    Compromissos **agendados** que ocupam a mesma faixa de horário — para a tela \
                    avisar antes de gravar.

                    **Avisa, não bloqueia:** perícia e audiência às vezes se sobrepõem de \
                    propósito, então nem `POST` nem `PUT` recusam por conflito. Quem decide é quem \
                    agenda.

                    Regra de sobreposição: `existente.startAt < endAt` **e** \
                    `existente.endAt > startAt`. Intervalos que só encostam (14h-15h e 15h-16h) \
                    **não** conflitam.

                    Cancelados e concluídos ficam de fora — não disputam horário com ninguém.

                    Na edição, informe `excludeId` para o compromisso não conflitar consigo mesmo.

                    Janela ausente ou invertida devolve lista vazia (é consulta, não gravação).
                    """)
    @GetMapping("/conflicts")
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> conflicts(
            @Parameter(
                            description = "Início da janela (ISO-8601)",
                            example = "2026-08-20T14:30:00Z")
                    @RequestParam
                    Instant startAt,
            @Parameter(
                            description = "Término da janela (ISO-8601)",
                            example = "2026-08-20T15:30:00Z")
                    @RequestParam
                    Instant endAt,
            @Parameter(description = "Compromisso sendo editado, ignorado na checagem")
                    @RequestParam(required = false)
                    UUID excludeId) {
        return ResponseEntity.ok(
                ApiResponse.successList(service.findConflicts(startAt, endAt, excludeId)));
    }

    @Operation(summary = "Detalhe de um compromisso")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(id)));
    }

    @Operation(
            summary = "Editar compromisso",
            description =
                    """
                    Substituição completa. Exige `justification`, registrada no histórico.

                    Só compromisso **agendado** pode ser editado: cancelado ou concluído devolve \
                    400 `OPERATION_NOT_ALLOWED`. Para remarcar algo já encerrado, crie um \
                    compromisso novo - assim a trilha guarda os dois fatos separados.""")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody AppointmentRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.successObject(service.update(id, dto)));
    }

    @Operation(
            summary = "Cancelar compromisso",
            description = "Marca como Cancelado. Exige `justification` (registrada no histórico).")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> cancel(
            @PathVariable UUID id, @Valid @RequestBody AppointmentCancelRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.cancel(id, dto.getJustification())));
    }

    @Operation(summary = "Concluir compromisso", description = "Marca como Concluído.")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.successObject(service.complete(id)));
    }

    @Operation(
            summary = "Excluir compromisso",
            description =
                    """
                    Exclusão **lógica**: o compromisso sai da agenda e do resumo por mês, mas \
                    continua no banco e volta por `PATCH /{id}/restore`.

                    Fica registrado na trilha (`GET /{id}/history`) como `DELETED`, com quem \
                    excluiu e quando. Não pede justificativa.

                    Excluir um compromisso já excluído devolve 404 - para a API ele não existe \
                    mais.""")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Restaurar compromisso excluído",
            description =
                    """
                    Desfaz a exclusão lógica e devolve o compromisso à agenda com o mesmo id, \
                    horário e histórico. Registra `RESTORED` na trilha.

                    Idempotente: restaurar um compromisso ativo devolve 200 com o compromisso, sem \
                    alterar nada e sem gerar registro na trilha. Id inexistente devolve 404.""")
    @PatchMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<AppointmentResponseDTO>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.successObject(service.restore(id)));
    }

    @Operation(
            summary = "Histórico de alterações",
            description = "Edições e cancelamentos com justificativa, mais recente primeiro.")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<AppointmentHistoryDTO>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<AppointmentHistoryDTO> page = service.history(id, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }
}
