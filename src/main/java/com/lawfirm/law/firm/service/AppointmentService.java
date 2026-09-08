package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.AppointmentHistoryDTO;
import com.lawfirm.law.firm.dto.AppointmentRequestDTO;
import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.AppointmentSearchParams;
import com.lawfirm.law.firm.dto.AppointmentSummaryDTO;
import com.lawfirm.law.firm.exception.BusinessErrorCode;
import com.lawfirm.law.firm.exception.BusinessException;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Appointment;
import com.lawfirm.law.firm.model.AppointmentAction;
import com.lawfirm.law.firm.model.AppointmentHistory;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.repository.AppointmentHistoryRepository;
import com.lawfirm.law.firm.repository.AppointmentRepository;
import com.lawfirm.law.firm.repository.AppointmentSpecification;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.RequestDates;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda do escritório (compromissos). Segue o padrão dos demais services: mapeamento manual {@code
 * toDTO}, soft delete, auditoria via {@code CurrentUser}, erros pela central. Edição e cancelamento
 * exigem justificativa, registrada em {@code appointment_history}.
 */
@Service
public class AppointmentService {

    private final AppointmentRepository repository;
    private final AppointmentHistoryRepository historyRepository;
    private final ClientRepository clientRepository;

    public AppointmentService(
            AppointmentRepository repository,
            AppointmentHistoryRepository historyRepository,
            ClientRepository clientRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public AppointmentResponseDTO create(AppointmentRequestDTO dto) {
        ensurePastDateAcknowledged(dto);

        Appointment entity = new Appointment();
        applyFields(entity, dto);
        entity.setStatus(AppointmentStatus.AGENDADO);
        entity.setCreatedBy(CurrentUser.id());
        boolean past = applyPastAuthorization(entity);
        Appointment saved = repository.save(entity);

        if (past) {
            recordAcknowledgement(saved);
        }
        return toDTO(saved);
    }

    /**
     * Compromissos que ocupam a mesma faixa de horário.
     *
     * <p><b>Avisa, não bloqueia.</b> Perícia e audiência às vezes se sobrepõem de propósito, e
     * recusar a gravação obrigaria a contornar o sistema — que é como um sistema deixa de refletir
     * a realidade. Quem decide é quem agenda; o backend só informa.
     *
     * <p>Só entram os <b>AGENDADOS</b>: um compromisso cancelado ou concluído não disputa horário
     * com ninguém, e trazê-lo faria a tela avisar de conflito com algo que não vai acontecer.
     *
     * @param excludeId compromisso sendo editado, para não conflitar consigo mesmo.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> findConflicts(
            Instant startAt, Instant endAt, UUID excludeId) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            // Janela inválida não tem conflito para relatar. A validação da janela é do
            // @AssertTrue no DTO de gravação; aqui é só consulta, e devolver vazio é
            // mais útil que um 400 numa checagem que a tela dispara enquanto se digita.
            return List.of();
        }

        Specification<Appointment> spec =
                AppointmentSpecification.combine(
                        List.of(
                                AppointmentSpecification.notDeleted(),
                                AppointmentSpecification.statusIn(
                                        List.of(AppointmentStatus.AGENDADO)),
                                AppointmentSpecification.overlaps(startAt, endAt),
                                AppointmentSpecification.idNot(excludeId)));

        List<Appointment> found = repository.findAll(spec, Sort.by(Sort.Direction.ASC, "startAt"));

        // Nomes em lote, como na listagem: `toDTO(entity)` sozinho resolve um cliente por
        // chamada, e a janela pode trazer vários compromissos.
        Map<UUID, String> names =
                clientNamesFor(found.stream().map(Appointment::getClientId).toList());
        return found.stream().map(a -> toDTO(a, names)).toList();
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponseDTO> list(AppointmentSearchParams params) {
        validateMonths(params.getMonth());
        boolean filterByYearOrMonth = !isEmpty(params.getYear()) || !isEmpty(params.getMonth());

        // Ano/mês (abas), agora em lista, têm prioridade sobre from/to - mesma regra de antes,
        // só que o período deixa de ser um intervalo contínuo e passa a ser "ano dentre os
        // informados" E "mês dentre os informados", cada filtro combinado independentemente.
        Specification<Appointment> periodSpec =
                filterByYearOrMonth
                        ? AppointmentSpecification.combine(
                                List.of(
                                        AppointmentSpecification.yearsIn(params.getYear()),
                                        AppointmentSpecification.monthsIn(params.getMonth())))
                        : AppointmentSpecification.startBetween(
                                RequestDates.parseInstant("from", params.getFrom(), true),
                                RequestDates.parseInstant("to", params.getTo(), false));

        List<AppointmentType> types =
                parseEnums(params.getType(), AppointmentType::fromLabel, "type");
        List<AppointmentStatus> statuses =
                parseEnums(params.getStatus(), AppointmentStatus::fromLabel, "status");

        Specification<Appointment> spec =
                AppointmentSpecification.combine(
                        List.of(
                                AppointmentSpecification.notDeleted(),
                                periodSpec,
                                AppointmentSpecification.typeIn(types),
                                AppointmentSpecification.statusIn(statuses),
                                AppointmentSpecification.clientIs(params.getClientId()),
                                AppointmentSpecification.titleContains(params.getSearchTerm())));

        Pageable pageable =
                PageRequest.of(
                        Math.max(0, params.getPageNumber() - 1),
                        params.getPageSize() <= 0 ? 10 : params.getPageSize(),
                        Sort.by(Sort.Direction.ASC, "startAt"));

        Page<Appointment> page = repository.findAll(spec, pageable);
        Map<UUID, String> names =
                clientNamesFor(page.getContent().stream().map(Appointment::getClientId).toList());
        return page.map(a -> toDTO(a, names));
    }

    /**
     * Contagem de compromissos PENDENTES (status Agendado) por mês do ano - alimenta as abas da
     * agenda. Conta só o que ainda está por fazer, então toda ação reflete no número: criar soma;
     * concluir, cancelar ou excluir subtrai. Concluídos/cancelados/excluídos não entram na aba.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSummaryDTO> summary(int year) {
        RequestDates.Range range = RequestDates.ofYear(year);

        Map<Integer, Long> byMonth = new TreeMap<>();
        for (Appointment a :
                repository.findByDeletedAtIsNullAndStartAtBetween(range.from(), range.to())) {
            if (a.getStatus() != AppointmentStatus.AGENDADO) {
                continue;
            }
            int month = a.getStartAt().atZone(ZoneOffset.UTC).getMonthValue();
            byMonth.merge(month, 1L, Long::sum);
        }
        return byMonth.entrySet().stream()
                .map(e -> new AppointmentSummaryDTO(year, e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponseDTO get(UUID id) {
        return toDTO(findOrThrow(id));
    }

    private static boolean isEmpty(List<Integer> values) {
        return values == null || values.isEmpty();
    }

    /**
     * Converte os valores de um filtro de enum (nome da constante OU label PT-BR) numa lista do
     * enum. Vazio/nulo vira lista vazia; valor inválido gera 400 explícito. Feito aqui, e não no
     * bind do {@code @ParameterObject}, porque o data binding de {@code List<Enum>} não aplica o
     * conversor de label - resolvendo o enum a partir de String no service (padrão do projeto).
     */
    private static <E> List<E> parseEnums(
            List<String> raw, Function<String, E> fromLabel, String field) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<E> parsed = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                parsed.add(fromLabel.apply(value));
            } catch (IllegalArgumentException ex) {
                throw new ValidationException(
                        field, ValidationErrorCode.INVALID_ENUM_VALUE, ex.getMessage());
            }
        }
        return parsed;
    }

    /** Cada mês precisa estar em 1-12 - valor fora do intervalo gera 400 explícito. */
    private static void validateMonths(List<Integer> months) {
        if (isEmpty(months)) {
            return;
        }
        for (Integer month : months) {
            if (month == null || month < 1 || month > 12) {
                throw new ValidationException(
                        "month", ValidationErrorCode.INVALID_DATE, "Mês inválido: " + month);
            }
        }
    }

    @Transactional
    public AppointmentResponseDTO update(UUID id, AppointmentRequestDTO dto) {
        Appointment entity = findOrThrow(id);
        String justification = requireJustification(dto.getJustification());
        ensurePastDateAcknowledged(dto);

        applyFields(entity, dto);
        entity.setUpdatedBy(CurrentUser.id());
        boolean past = applyPastAuthorization(entity);
        Appointment saved = repository.save(entity);

        recordHistory(saved, AppointmentAction.EDITED, justification);
        if (past) {
            recordAcknowledgement(saved);
        }
        return toDTO(saved);
    }

    @Transactional
    public AppointmentResponseDTO cancel(UUID id, String justification) {
        Appointment entity = findOrThrow(id);
        String reason = requireJustification(justification);
        if (entity.getStatus() == AppointmentStatus.CANCELADO) {
            throw new BusinessException(
                    BusinessErrorCode.OPERATION_NOT_ALLOWED, "Compromisso já está cancelado.");
        }
        entity.setStatus(AppointmentStatus.CANCELADO);
        entity.setCancellationReason(reason);
        entity.setUpdatedBy(CurrentUser.id());
        Appointment saved = repository.save(entity);

        recordHistory(saved, AppointmentAction.CANCELLED, reason);
        return toDTO(saved);
    }

    @Transactional
    public AppointmentResponseDTO complete(UUID id) {
        Appointment entity = findOrThrow(id);
        if (entity.getStatus() == AppointmentStatus.CANCELADO) {
            throw new BusinessException(
                    BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Compromisso cancelado não pode ser concluído.");
        }
        entity.setStatus(AppointmentStatus.CONCLUIDO);
        entity.setUpdatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        Appointment entity = findOrThrow(id);
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(CurrentUser.id());
        repository.save(entity);
    }

    public Page<AppointmentHistoryDTO> history(UUID id, int pageNumber, int pageSize) {
        findOrThrow(id);
        Pageable pageable =
                PageRequest.of(
                        Math.max(0, pageNumber - 1),
                        pageSize <= 0 ? 10 : pageSize,
                        Sort.by(Sort.Direction.DESC, "changedAt"));
        return historyRepository.findByAppointment_Id(id, pageable).map(this::toHistoryDTO);
    }

    // ── Private helpers ──

    private Appointment findOrThrow(UUID id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Compromisso", id));
    }

    private void applyFields(Appointment entity, AppointmentRequestDTO dto) {
        entity.setTitle(dto.getTitle());
        entity.setType(dto.getType());
        entity.setStartAt(dto.getStartAt());
        entity.setEndAt(dto.getEndAt());
        entity.setModality(
                dto.getModality() != null ? dto.getModality() : AppointmentModality.PRESENCIAL);
        entity.setLocation(dto.getLocation());
        entity.setMeetingUrl(dto.getMeetingUrl());
        entity.setDescription(dto.getDescription());

        // Vínculo com cliente: se veio clientId, valida e usa o cliente (nome vem dele);
        // senão, aceita o nome livre digitado (pessoa ainda não cadastrada) - nunca 404.
        UUID clientId = resolveClientId(dto.getClientId());
        entity.setClientId(clientId);
        entity.setClientName(clientId != null ? null : trimToNull(dto.getClientName()));
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Valida que o cliente existe (quando informado) antes de ligar a FK. */
    private UUID resolveClientId(UUID clientId) {
        if (clientId == null) {
            return null;
        }
        ClientLookup.orThrow(clientRepository, clientId);
        return clientId;
    }

    private static String requireJustification(String justification) {
        if (justification == null || justification.isBlank()) {
            throw new ValidationException(
                    "justification",
                    ValidationErrorCode.REQUIRED_FIELD,
                    "Justificativa é obrigatória.");
        }
        return justification.trim();
    }

    private static boolean isPast(Instant startAt) {
        return startAt != null && startAt.isBefore(Instant.now());
    }

    /**
     * Regra: compromisso com data (start) no passado só é aceito se o usuário confirmar ciência.
     * Sem a confirmação, rejeita com 422 para o front pedir a confirmação; com ela, a ciência é
     * registrada no histórico (ver {@link #recordAcknowledgement}).
     */
    private static void ensurePastDateAcknowledged(AppointmentRequestDTO dto) {
        if (isPast(dto.getStartAt()) && !dto.isPastDateAcknowledged()) {
            throw new BusinessException(BusinessErrorCode.PAST_DATE_NOT_CONFIRMED);
        }
    }

    private void recordAcknowledgement(Appointment saved) {
        recordHistory(
                saved,
                AppointmentAction.ACKNOWLEDGED,
                "Ciência confirmada pelo usuário: compromisso com data no passado (início "
                        + saved.getStartAt()
                        + ").");
    }

    /** Registra quem autorizou (usuário corrente) e quando, se o compromisso é retroativo. */
    private boolean applyPastAuthorization(Appointment entity) {
        if (isPast(entity.getStartAt())) {
            entity.setPastDateAuthorizedBy(CurrentUser.id());
            entity.setPastDateAuthorizedAt(Instant.now());
            return true;
        }
        return false;
    }

    private void recordHistory(
            Appointment appointment, AppointmentAction action, String justification) {
        AppointmentHistory h = new AppointmentHistory();
        h.setAppointment(appointment);
        h.setAction(action);
        h.setJustification(justification);
        h.setChangedAt(Instant.now());
        h.setChangedBy(CurrentUser.id());
        historyRepository.save(h);
    }

    private AppointmentResponseDTO toDTO(Appointment entity) {
        UUID clientId = entity.getClientId();
        Map<UUID, String> names = clientId == null ? Map.of() : clientNamesFor(List.of(clientId));
        return toDTO(entity, names);
    }

    private AppointmentResponseDTO toDTO(Appointment entity, Map<UUID, String> clientNames) {
        AppointmentResponseDTO dto = new AppointmentResponseDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setType(entity.getType() != null ? entity.getType().getLabel() : null);
        dto.setStartAt(entity.getStartAt());
        dto.setEndAt(entity.getEndAt());
        dto.setModality(entity.getModality() != null ? entity.getModality().getLabel() : null);
        dto.setLocation(entity.getLocation());
        dto.setMeetingUrl(entity.getMeetingUrl());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setCancellationReason(entity.getCancellationReason());
        dto.setClientId(entity.getClientId());
        // Nome exibido: do cliente vinculado (fresco) ou o nome livre digitado.
        dto.setClientName(
                entity.getClientId() != null
                        ? clientNames.get(entity.getClientId())
                        : entity.getClientName());
        dto.setPastDateAuthorizedBy(entity.getPastDateAuthorizedBy());
        dto.setPastDateAuthorizedAt(entity.getPastDateAuthorizedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /** Resolve nome (fullName) dos clientes vinculados em uma única consulta (evita N+1). */
    private Map<UUID, String> clientNamesFor(Collection<UUID> clientIds) {
        List<UUID> ids = clientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (Client c : clientRepository.findAllById(ids)) {
            names.put(c.getId(), c.getFullName());
        }
        return names;
    }

    private AppointmentHistoryDTO toHistoryDTO(AppointmentHistory h) {
        return new AppointmentHistoryDTO(
                h.getId(),
                h.getAction() != null ? h.getAction().name() : null,
                h.getJustification(),
                h.getChangedAt(),
                h.getChangedBy());
    }
}
