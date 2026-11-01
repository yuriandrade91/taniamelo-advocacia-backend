package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.AuditIntent;
import com.lawfirm.law.firm.audit.AuditLog;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInssPasswordDTO;
import com.lawfirm.law.firm.dto.ClientListFilter;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientRequestStageHistoryDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.dto.UpcomingBirthdayDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientRequestStageHistory;
import com.lawfirm.law.firm.model.ClientSituationHistory;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.BirthdayRow;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.ClientRequestStageHistoryRepository;
import com.lawfirm.law.firm.repository.ClientSituationHistoryRepository;
import com.lawfirm.law.firm.repository.ClientSpecification;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.Birthdays;
import com.lawfirm.law.firm.util.DocumentoIdentidade;
import com.lawfirm.law.firm.util.OfficeClock;
import com.lawfirm.law.firm.util.PageRequests;
import com.lawfirm.law.firm.util.TempoDeContribuicao;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientServiceImpl implements ClientService {

    private static final String FIELD_BENEFICIARY_NUMBER = "beneficiaryNumber";
    private static final String FIELD_NIT_PIS = "nitPis";
    private static final String MSG_BENEFICIARY_NUMBER_DUPLICADO =
            "Número do benefício já cadastrado";
    private static final String MSG_CPF_DUPLICADO = "CPF já cadastrado";
    private static final String MSG_RG_DUPLICADO = "RG já cadastrado";
    private static final String MSG_NIT_PIS_DUPLICADO = "NIT/PIS já cadastrado";
    private static final String MSG_CTPS_DUPLICADO = "CTPS já cadastrado";

    private final ClientRepository repository;
    private final ClientMapper mapper;
    private final ClientSituationHistoryRepository historyRepository;
    private final ClientRequestStageHistoryRepository requestStageHistoryRepository;
    private final AuditLogRepository auditRepository;

    public ClientServiceImpl(
            ClientRepository repository,
            ClientMapper mapper,
            ClientSituationHistoryRepository historyRepository,
            ClientRequestStageHistoryRepository requestStageHistoryRepository,
            AuditLogRepository auditRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.historyRepository = historyRepository;
        this.requestStageHistoryRepository = requestStageHistoryRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional
    public ClientDetailsDTO create(ClientCreateRequestDTO dto) {
        Client entity = mapper.toEntity(dto);
        normalizarDocumentosIdentidade(entity);
        validateUniqueness(entity);
        entity.setCreatedBy(CurrentUser.id());
        entity.setUpdatedBy(CurrentUser.id());
        entity.setContributionInMonths(
                TempoDeContribuicao.emMeses(
                        entity.getContributionYears(),
                        entity.getContributionMonths(),
                        entity.getContributionDays()));
        Client saved = repository.save(entity);
        recordHistory(null, saved);
        return mapper.toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientListResponseDTO> listSummary(
            int pageNumber, int pageSize, ClientListFilter filter) {
        Specification<Client> spec =
                ClientSpecification.combine(
                        List.of(
                                ClientSpecification.searchTerm(filter.searchTerm()),
                                ClientSpecification.benefitIn(filter.benefitTypes()),
                                ClientSpecification.situationIn(filter.situations()),
                                ClientSpecification.requestStageIn(filter.requestStages()),
                                ClientSpecification.clientTypeIn(filter.clientTypes()),
                                ClientSpecification.notBillableIs(filter.notBillable()),
                                ClientSpecification.createdBetween(
                                        filter.createdFrom(), filter.createdTo())));

        Pageable pageable =
                PageRequests.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Client> page = repository.findAll(spec, pageable);
        return page.map(mapper::toListDTO);
    }

    /**
     * A lixeira: clientes excluídos, mais recentes primeiro.
     *
     * <p>Sem {@code Sort} no {@code Pageable}, de propósito: a consulta é nativa e já ordena por
     * {@code deleted_at}; um {@code Sort.by("deletedAt")} é anexado ao SQL com o nome Java e quebra
     * com "column deletedat does not exist". Nativa porque {@code @SQLRestriction} esconde excluído
     * de toda consulta JPQL (que é o que se quer em todo o resto).
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ClientListResponseDTO> listDeleted(int pageNumber, int pageSize) {
        return repository.findDeleted(PageRequests.of(pageNumber, pageSize)).map(mapper::toListDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDetailsDTO> findById(UUID id) {
        return repository.findById(id).map(mapper::toDTO);
    }

    @Override
    @Transactional
    public ClientDetailsDTO update(UUID id, ClientUpdateRequestDTO dto) {
        Client existing = findOrThrow(id);
        Situation previousSituation = existing.getSituation();
        RequestStage previousStage = existing.getRequestStage();
        String justification =
                justificativaDaTransicao(
                        previousStage, dto.getRequestStage(), dto.getRequestStageJustification());

        mapper.updateEntityFromDto(dto, existing);
        normalizarDocumentosIdentidade(existing);
        validateUniqueness(existing, id);
        existing.setContributionInMonths(
                TempoDeContribuicao.emMeses(
                        existing.getContributionYears(),
                        existing.getContributionMonths(),
                        existing.getContributionDays()));
        existing.setUpdatedBy(CurrentUser.id());
        Client saved = repository.save(existing);

        if (!Objects.equals(previousSituation, saved.getSituation())) {
            recordHistory(previousSituation, saved);
        }
        if (!Objects.equals(previousStage, saved.getRequestStage())) {
            recordRequestStageHistory(saved, previousStage, saved.getRequestStage(), justification);
        }
        return mapper.toDTO(saved);
    }

    @Override
    @Transactional
    public ClientPatchOutcome patch(UUID id, ClientPatchRequestDTO patch) {
        if (!patch.camposNaoSuportados().isEmpty()) {
            String unsupported = String.join(", ", patch.camposNaoSuportados());
            throw new ValidationException(
                    "payload",
                    ValidationErrorCode.CONFLICTING_PARAMETERS,
                    "Campos não suportados no PATCH: " + unsupported);
        }
        if (patch.getRequestStage() == null && patch.getRequestStageJustification() != null) {
            throw new ValidationException(
                    "requestStageJustification",
                    ValidationErrorCode.CONFLICTING_PARAMETERS,
                    "requestStageJustification exige requestStage");
        }

        Client existing = findOrThrow(id);
        Situation previousSituation = existing.getSituation();
        RequestStage previousStage = existing.getRequestStage();
        BenefitType previousBenefit = existing.getBenefit();
        ClientType previousType = existing.getClientType();
        Boolean previousNotBillable = existing.getNotBillable();
        String justification = null;
        boolean situationChanged = false;
        boolean requestStageChanged = false;
        boolean benefitChanged = false;
        boolean clientTypeChanged = false;
        boolean notBillableChanged = false;

        situationChanged =
                applyEnumPatch(
                        patch.getSituation(),
                        previousSituation,
                        existing::setSituation,
                        "situation",
                        Situation::fromLabel,
                        ValidationErrorCode.INVALID_SITUATION);
        benefitChanged =
                applyEnumPatch(
                        patch.getBenefit(),
                        previousBenefit,
                        existing::setBenefit,
                        "benefit",
                        BenefitType::fromLabel,
                        ValidationErrorCode.INVALID_BENEFIT);
        clientTypeChanged =
                applyEnumPatch(
                        patch.getClientType(),
                        previousType,
                        existing::setClientType,
                        "clientType",
                        ClientType::fromLabel,
                        ValidationErrorCode.INVALID_CLIENT_TYPE);
        if (patch.getNotBillable() != null) {
            notBillableChanged = !Objects.equals(previousNotBillable, patch.getNotBillable());
            if (notBillableChanged) {
                existing.setNotBillable(patch.getNotBillable());
            }
        }

        if (patch.getRequestStage() != null) {
            RequestStage newStage =
                    parseEnum(
                            patch.getRequestStage(),
                            "requestStage",
                            RequestStage::fromLabel,
                            ValidationErrorCode.INVALID_REQUEST_STAGE);
            requestStageChanged = !Objects.equals(previousStage, newStage);
            if (requestStageChanged) {
                justification =
                        justificativaDaTransicao(
                                previousStage, newStage, patch.getRequestStageJustification());
                existing.setRequestStage(newStage);
            }
        }

        if (!situationChanged
                && !requestStageChanged
                && !benefitChanged
                && !clientTypeChanged
                && !notBillableChanged) {
            return new ClientPatchOutcome(false, false, false, false, false);
        }

        existing.setUpdatedBy(CurrentUser.id());
        Client saved = repository.save(existing);

        if (situationChanged) {
            recordHistory(previousSituation, saved);
        }
        if (requestStageChanged) {
            recordRequestStageHistory(saved, previousStage, saved.getRequestStage(), justification);
        }

        return new ClientPatchOutcome(
                situationChanged,
                requestStageChanged,
                benefitChanged,
                clientTypeChanged,
                notBillableChanged);
    }

    public static String justificativaDaTransicao(
            RequestStage etapaAnterior, RequestStage novaEtapa, String justificativa) {
        if (novaEtapa == null || Objects.equals(etapaAnterior, novaEtapa)) {
            return null;
        }
        if (novaEtapa.exigeJustificativa() && (justificativa == null || justificativa.isBlank())) {
            throw new ValidationException(
                    "requestStageJustification",
                    ValidationErrorCode.REQUIRED_FIELD,
                    "Justificativa obrigatória para mudança para " + novaEtapa.getLabel());
        }
        return justificativa == null ? null : justificativa.trim();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Client client = findOrThrow(id);
        if (client.getDeletedAt() != null) {
            return;
        }
        client.setDeletedAt(Instant.now());
        client.setUpdatedBy(CurrentUser.id());
        AuditIntent.declaring(AuditAction.DELETE, () -> repository.saveAndFlush(client));
    }

    @Override
    @Transactional
    public void restore(UUID id) {
        Client client =
                repository
                        .findByIdIncludingDeleted(id)
                        .orElseThrow(() -> NotFoundException.of("Cliente", id));
        if (client.getDeletedAt() == null) {
            return;
        }
        client.setDeletedAt(null);
        client.setUpdatedBy(CurrentUser.id());
        AuditIntent.declaring(AuditAction.RESTORE, () -> repository.saveAndFlush(client));
    }

    /**
     * Transação de escrita, mesmo sendo uma leitura: em {@code readOnly} o Hibernate não faz flush,
     * e o registro de que alguém viu a senha nunca chegaria ao banco.
     */
    @Override
    @Transactional
    public ClientInssPasswordDTO revealInssPassword(UUID id) {
        Client client = findOrThrow(id);
        auditRepository.save(
                new AuditLog(
                        "Client",
                        client.getId(),
                        AuditAction.READ,
                        CurrentUser.id(),
                        CurrentUser.actingSupportId(),
                        "INSS password revealed"));
        return new ClientInssPasswordDTO(client.getInssPassword());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientSituationHistoryDTO> historyByClientId(
            UUID clientId, int pageNumber, int pageSize) {
        findOrThrow(clientId);
        Pageable pageable =
                PageRequests.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "changedAt"));
        return historyRepository.findByClient_Id(clientId, pageable).map(mapper::toHistoryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClientRequestStageHistoryDTO> requestStageHistoryByClientId(
            UUID clientId, int pageNumber, int pageSize) {
        findOrThrow(clientId);
        Pageable pageable =
                PageRequests.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "changedAt"));
        return requestStageHistoryRepository
                .findByClient_Id(clientId, pageable)
                .map(mapper::toRequestStageHistoryDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientPersonalDataResponseDTO getPersonalData(UUID id) {
        return mapPersonalData(findOrThrow(id));
    }

    @Override
    @Transactional
    public ClientPersonalDataResponseDTO updatePersonalData(
            UUID id, ClientPersonalDataRequestDTO dto) {
        Client existing = findOrThrow(id);
        existing.setFullName(dto.getFullName());
        existing.setBirthDate(dto.getBirthDate());
        existing.setCpf(DocumentoIdentidade.apenasDigitos(dto.getCpf()));
        existing.setRg(DocumentoIdentidade.alfanumericoMaiusculo(dto.getRg()));
        existing.setRgIssuer(dto.getRgIssuer());
        existing.setRgIssueDate(dto.getRgIssueDate());
        existing.setMotherName(dto.getMotherName());
        existing.setGender(dto.getGender());
        existing.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getNationality() != null) existing.setNationality(dto.getNationality());
        existing.setMobilePhone(dto.getMobilePhone());
        existing.setReferencePhone(dto.getReferencePhone());
        existing.setReferenceResponsible(dto.getReferenceResponsible());
        if (dto.getIsWhatsapp() != null) existing.setIsWhatsapp(dto.getIsWhatsapp());
        existing.setEmail(dto.getEmail());
        if (dto.getHasDisability() != null) existing.setHasDisability(dto.getHasDisability());
        existing.setUpdatedBy(CurrentUser.id());
        return mapPersonalData(repository.save(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public ClientProfessionalDataResponseDTO getProfessionalData(UUID id) {
        return mapProfessionalData(findOrThrow(id));
    }

    @Override
    @Transactional
    public ClientProfessionalDataResponseDTO updateProfessionalData(
            UUID id, ClientProfessionalDataRequestDTO dto) {
        Client existing = findOrThrow(id);
        String nitPis = DocumentoIdentidade.apenasDigitos(dto.getNitPis());
        String ctps = DocumentoIdentidade.apenasDigitos(dto.getCtps());
        String beneficiaryNumber = DocumentoIdentidade.apenasDigitos(dto.getBeneficiaryNumber());
        // Antes de tocar na entidade: com ela alterada, a própria consulta faria o flush e quem
        // responderia seria o índice do banco, sem dizer qual campo está duplicado.
        validateProfessionalDocuments(id, beneficiaryNumber, nitPis, ctps);

        existing.setProfession(dto.getProfession());
        existing.setNitPis(nitPis);
        existing.setCtps(ctps);
        existing.setCtpsSeries(dto.getCtpsSeries());
        existing.setContributionYears(dto.getContributionYears());
        existing.setContributionMonths(dto.getContributionMonths());
        existing.setContributionDays(dto.getContributionDays());
        existing.setContributionInMonths(
                TempoDeContribuicao.emMeses(
                        dto.getContributionYears(),
                        dto.getContributionMonths(),
                        dto.getContributionDays()));
        existing.setBeneficiaryNumber(beneficiaryNumber);
        if (dto.getInssPassword() != null && !dto.getInssPassword().isBlank()) {
            existing.setInssPassword(dto.getInssPassword());
        }
        existing.setUpdatedBy(CurrentUser.id());
        return mapProfessionalData(repository.save(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UpcomingBirthdayDTO> upcomingBirthdays(int days, int limit) {
        int windowDays = Math.clamp(days, 0, 365);
        int pageSize = Math.clamp(limit, 1, 50);
        LocalDate today = OfficeClock.today();
        LocalDate end = today.plusDays(windowDays);
        short fromMmdd = toMmdd(today);
        short toMmdd = toMmdd(end);
        boolean wraps = toMmdd < fromMmdd;

        List<BirthdayRow> rows =
                repository.findBirthdaysInWindow(
                        fromMmdd, toMmdd, wraps, false, PageRequest.of(0, pageSize));
        return rows.stream().map(row -> toBirthday(row, today)).toList();
    }

    private Client findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Cliente", id));
    }

    private void validateUniqueness(Client entity) {
        checkDuplicate(
                entity.getBeneficiaryNumber(),
                repository::existsByBeneficiaryNumber,
                FIELD_BENEFICIARY_NUMBER,
                MSG_BENEFICIARY_NUMBER_DUPLICADO);
        checkDuplicate(entity.getCpf(), repository::existsByCpf, "cpf", MSG_CPF_DUPLICADO);
        checkDuplicate(entity.getRg(), repository::existsByRg, "rg", MSG_RG_DUPLICADO);
        checkDuplicate(
                entity.getNitPis(),
                repository::existsByNitPis,
                FIELD_NIT_PIS,
                MSG_NIT_PIS_DUPLICADO);
        checkDuplicate(entity.getCtps(), repository::existsByCtps, "ctps", MSG_CTPS_DUPLICADO);
    }

    private void validateUniqueness(Client entity, UUID id) {
        checkDuplicate(
                entity.getCpf(),
                value -> repository.existsByCpfAndIdNot(value, id),
                "cpf",
                MSG_CPF_DUPLICADO);
        checkDuplicate(
                entity.getRg(),
                value -> repository.existsByRgAndIdNot(value, id),
                "rg",
                MSG_RG_DUPLICADO);
        validateProfessionalDocuments(
                id, entity.getBeneficiaryNumber(), entity.getNitPis(), entity.getCtps());
    }

    private void validateProfessionalDocuments(
            UUID id, String beneficiaryNumber, String nitPis, String ctps) {
        checkDuplicate(
                beneficiaryNumber,
                value -> repository.existsByBeneficiaryNumberAndIdNot(value, id),
                FIELD_BENEFICIARY_NUMBER,
                MSG_BENEFICIARY_NUMBER_DUPLICADO);
        checkDuplicate(
                nitPis,
                value -> repository.existsByNitPisAndIdNot(value, id),
                FIELD_NIT_PIS,
                MSG_NIT_PIS_DUPLICADO);
        checkDuplicate(
                ctps,
                value -> repository.existsByCtpsAndIdNot(value, id),
                "ctps",
                MSG_CTPS_DUPLICADO);
    }

    private void normalizarDocumentosIdentidade(Client entity) {
        entity.setCpf(DocumentoIdentidade.apenasDigitos(entity.getCpf()));
        entity.setRg(DocumentoIdentidade.alfanumericoMaiusculo(entity.getRg()));
        entity.setCtps(DocumentoIdentidade.apenasDigitos(entity.getCtps()));
        entity.setNitPis(DocumentoIdentidade.apenasDigitos(entity.getNitPis()));
        entity.setBeneficiaryNumber(
                DocumentoIdentidade.apenasDigitos(entity.getBeneficiaryNumber()));
    }

    private void checkDuplicate(
            String value,
            java.util.function.Predicate<String> existsFn,
            String field,
            String message) {
        String normalized = normalizeUniqueValue(value, field);
        if (normalized != null && existsFn.test(normalized)) {
            throw new ValidationException(field, ValidationErrorCode.DUPLICATE_VALUE, message);
        }
    }

    private static String normalizeUniqueValue(String value, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return switch (field) {
            case FIELD_BENEFICIARY_NUMBER, "cpf", FIELD_NIT_PIS, "ctps" ->
                    DocumentoIdentidade.apenasDigitos(trimmed);
            case "rg" -> DocumentoIdentidade.alfanumericoMaiusculo(trimmed);
            default -> DocumentoIdentidade.aparado(trimmed);
        };
    }

    // Chamado só de dentro de create/update/patch, já @Transactional: entra na mesma
    // transação por propagação, então uma anotação própria aqui seria decorativa - e, pior,
    // sugeriria (errado) que a chamada por "this" abre transação própria via proxy do Spring.
    protected void recordHistory(Situation previous, Client saved) {
        ClientSituationHistory h = new ClientSituationHistory();
        h.setClient(saved);
        h.setPreviousSituation(previous == null ? null : previous.getLabel());
        h.setNewSituation(saved.getSituation() == null ? null : saved.getSituation().getLabel());
        h.setChangedAt(Instant.now());
        h.setChangedBy(CurrentUser.id());
        historyRepository.save(h);
    }

    protected void recordRequestStageHistory(
            Client client,
            RequestStage previousStage,
            RequestStage newStage,
            String justification) {
        if (Objects.equals(previousStage, newStage)) {
            return;
        }
        ClientRequestStageHistory h = new ClientRequestStageHistory();
        h.setClient(client);
        h.setPreviousStage(previousStage == null ? null : previousStage.getLabel());
        h.setNewStage(newStage == null ? null : newStage.getLabel());
        h.setPreviousStageEnteredAt(resolvePreviousStageEnteredAt(client.getId(), previousStage));
        h.setChangedAt(Instant.now());
        h.setChangedBy(CurrentUser.id());
        h.setJustification(justification);
        requestStageHistoryRepository.save(h);
    }

    private Instant resolvePreviousStageEnteredAt(UUID clientId, RequestStage previousStage) {
        if (previousStage == null) {
            return null;
        }
        return requestStageHistoryRepository
                .findFirstByClient_IdOrderByChangedAtDesc(clientId)
                .map(ClientRequestStageHistory::getChangedAt)
                .orElse(null);
    }

    private ClientPersonalDataResponseDTO mapPersonalData(Client entity) {
        ClientPersonalDataResponseDTO dto = new ClientPersonalDataResponseDTO();
        dto.setId(entity.getId());
        dto.setFullName(entity.getFullName());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setBirthDate(entity.getBirthDate());
        if (entity.getBirthDate() != null) {
            dto.setAge((int) ChronoUnit.YEARS.between(entity.getBirthDate(), OfficeClock.today()));
        }
        dto.setCpf(entity.getCpf());
        dto.setRg(entity.getRg());
        dto.setRgIssuer(entity.getRgIssuer());
        dto.setRgIssueDate(entity.getRgIssueDate());
        dto.setMotherName(entity.getMotherName());
        dto.setGender(entity.getGender());
        dto.setMaritalStatus(entity.getMaritalStatus());
        dto.setNationality(entity.getNationality());
        dto.setMobilePhone(entity.getMobilePhone());
        dto.setReferencePhone(entity.getReferencePhone());
        dto.setReferenceResponsible(entity.getReferenceResponsible());
        dto.setIsWhatsapp(entity.getIsWhatsapp());
        dto.setEmail(entity.getEmail());
        dto.setHasDisability(entity.getHasDisability());
        dto.setProfession(entity.getProfession());
        dto.setNitPis(entity.getNitPis());
        dto.setCtps(entity.getCtps());
        dto.setCtpsSeries(entity.getCtpsSeries());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ClientProfessionalDataResponseDTO mapProfessionalData(Client entity) {
        ClientProfessionalDataResponseDTO dto = new ClientProfessionalDataResponseDTO();
        dto.setClientId(entity.getId());
        dto.setProfession(entity.getProfession());
        dto.setNitPis(entity.getNitPis());
        dto.setCtps(entity.getCtps());
        dto.setCtpsSeries(entity.getCtpsSeries());
        dto.setContributionYears(entity.getContributionYears());
        dto.setContributionMonths(entity.getContributionMonths());
        dto.setContributionDays(entity.getContributionDays());
        dto.setContributionTime(
                TempoDeContribuicao.formatar(
                        entity.getContributionYears(),
                        entity.getContributionMonths(),
                        entity.getContributionDays()));
        dto.setContributionInMonths(entity.getContributionInMonths());
        dto.setBeneficiaryNumber(entity.getBeneficiaryNumber());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /**
     * Um campo do PATCH: ausente ({@code raw == null}) não toca o cliente; presente, parseia,
     * compara com o valor atual e aplica via {@code setter} só se mudou. Devolve se mudou, para o
     * chamador decidir o que depende disso (histórico, {@link ClientPatchOutcome}).
     */
    private static <T extends Enum<T>> boolean applyEnumPatch(
            String raw,
            T previous,
            Consumer<T> setter,
            String field,
            Function<String, T> parser,
            ValidationErrorCode code) {
        if (raw == null) {
            return false;
        }
        T newValue = parseEnum(raw, field, parser, code);
        boolean changed = !Objects.equals(previous, newValue);
        if (changed) {
            setter.accept(newValue);
        }
        return changed;
    }

    private static <T extends Enum<T>> T parseEnum(
            String raw, String field, Function<String, T> parser, ValidationErrorCode code) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(field, code, "Valor inválido para " + field + ": " + raw);
        }
        try {
            return parser.apply(trimmed);
        } catch (RuntimeException _) {
            throw new ValidationException(field, code, "Valor inválido para " + field + ": " + raw);
        }
    }

    private static short toMmdd(LocalDate date) {
        return (short) ((date.getMonthValue() * 100) + date.getDayOfMonth());
    }

    private static UpcomingBirthdayDTO toBirthday(BirthdayRow row, LocalDate today) {
        LocalDate nextBirthday = Birthdays.next(row.birthDate(), today);
        long daysUntil = ChronoUnit.DAYS.between(today, nextBirthday);
        int turningAge = nextBirthday.getYear() - row.birthDate().getYear();
        return new UpcomingBirthdayDTO(
                row.clientId(),
                row.fullName(),
                row.birthDate(),
                nextBirthday,
                daysUntil,
                turningAge,
                row.mobilePhone(),
                Boolean.TRUE.equals(row.isWhatsapp()));
    }
}
