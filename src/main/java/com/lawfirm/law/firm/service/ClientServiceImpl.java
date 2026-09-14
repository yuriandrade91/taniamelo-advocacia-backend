package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.AuditLog;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientInssPasswordDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientSituationHistory;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.ClientSituationHistoryRepository;
import com.lawfirm.law.firm.repository.ClientSpecification;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.ContributionTimeParser;
import com.lawfirm.law.firm.util.DocumentoIdentidade;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository repository;
    private final ClientMapper mapper;
    private final ClientSituationHistoryRepository historyRepository;
    private final AuditLogRepository auditRepository;

    public ClientServiceImpl(
            ClientRepository repository,
            ClientMapper mapper,
            ClientSituationHistoryRepository historyRepository,
            AuditLogRepository auditRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.historyRepository = historyRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional
    public ClientDetailsDTO create(ClientCreateRequestDTO dto) {
        Client entity = mapper.toEntity(dto);
        normalizarIdentidade(entity);
        validarIdentidadeUnica(entity, null);
        entity.setContributionInMonths(
                ContributionTimeParser.toMonths(entity.getContributionTime()));
        entity.setCreatedBy(CurrentUser.id());
        Client saved = repository.save(entity);
        recordHistory(null, saved);
        return mapper.toDTO(saved);
    }

    @Override
    public Page<ClientListResponseDTO> listSummary(
            int pageNumber,
            int pageSize,
            String searchTerm,
            List<BenefitType> benefitTypes,
            List<Situation> situations,
            List<ClientType> clientTypes,
            Instant createdFrom,
            Instant createdTo) {
        Specification<Client> spec =
                ClientSpecification.combine(
                        List.of(
                                ClientSpecification.searchTerm(searchTerm),
                                ClientSpecification.benefitIn(benefitTypes),
                                ClientSpecification.situationIn(situations),
                                ClientSpecification.clientTypeIn(clientTypes),
                                ClientSpecification.createdBetween(createdFrom, createdTo)));

        // updatedAt é sempre populado (prePersist/preUpdate), então ordenar por ele
        // dá "atividade mais recente primeiro" sem query manual.
        var pageable =
                PageRequests.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return repository.findAll(spec, pageable).map(mapper::toListDTO);
    }

    @Override
    public Optional<ClientDetailsDTO> findById(UUID id) {
        return repository.findById(id).map(mapper::toDTO);
    }

    @Override
    @Transactional
    public ClientDetailsDTO update(UUID id, ClientUpdateRequestDTO dto) {
        Client existing = findOrThrow(id);
        Situation previous = existing.getSituation();

        validarIdentidadeUnica(
                id,
                dto.getCpf(),
                dto.getRg(),
                dto.getCtps(),
                dto.getNitPis(),
                dto.getBeneficiaryNumber());
        mapper.updateEntityFromDto(dto, existing);
        normalizarIdentidade(existing);
        existing.setContributionInMonths(
                ContributionTimeParser.toMonths(existing.getContributionTime()));
        existing.setUpdatedBy(CurrentUser.id());

        Client saved = repository.save(existing);
        if (!Objects.equals(previous, saved.getSituation())) {
            recordHistory(previous, saved);
        }
        return mapper.toDTO(saved);
    }

    @Override
    @Transactional
    public ClientPatchOutcome patch(UUID id, ClientPatchRequestDTO patch) {
        Client existing = findOrThrow(id);

        boolean situationChanged = false;
        boolean benefitChanged = false;
        boolean clientTypeChanged = false;
        boolean notBillableChanged = false;

        if (patch.getSituation() != null) {
            Situation incoming = parseSituation(patch.getSituation());
            Situation previous = existing.getSituation();
            if (!Objects.equals(previous, incoming)) {
                existing.setSituation(incoming);
                situationChanged = true;
                existing.setUpdatedBy(CurrentUser.id());
                Client saved = repository.save(existing);
                recordHistory(previous, saved);
                existing = saved;
            }
        }

        if (patch.getBenefit() != null) {
            BenefitType incoming = parseBenefit(patch.getBenefit());
            if (!Objects.equals(existing.getBenefit(), incoming)) {
                existing.setBenefit(incoming);
                existing.setUpdatedBy(CurrentUser.id());
                existing = repository.save(existing);
                benefitChanged = true;
            }
        }

        if (patch.getClientType() != null) {
            ClientType incoming = parseClientType(patch.getClientType());
            if (!Objects.equals(existing.getClientType(), incoming)) {
                existing.setClientType(incoming);
                existing.setUpdatedBy(CurrentUser.id());
                existing = repository.save(existing);
                clientTypeChanged = true;
            }
        }

        if (patch.getNotBillable() != null
                && !Objects.equals(existing.getNotBillable(), patch.getNotBillable())) {
            existing.setNotBillable(patch.getNotBillable());
            existing.setUpdatedBy(CurrentUser.id());
            repository.save(existing);
            notBillableChanged = true;
        }

        return new ClientPatchOutcome(
                situationChanged, benefitChanged, clientTypeChanged, notBillableChanged);
    }

    /**
     * Exclusão lógica. Não apaga a linha: as FKs de endereços, entrevistas, arquivos, pagamentos e
     * histórico são {@code ON DELETE CASCADE}, então um delete de verdade levaria a ficha inteira
     * junto - inclusive o histórico, que é o registro do que aconteceu. O cliente some das
     * consultas pelo {@code @SQLRestriction} da entidade e volta por {@link #restore(UUID)}.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        Client existing = findOrThrow(id);
        existing.setDeletedAt(Instant.now());
        existing.setUpdatedBy(CurrentUser.id());
        repository.save(existing);
    }

    /**
     * Desfaz a exclusão lógica. Idempotente: restaurar um cliente ativo não é erro, só não muda
     * nada - quem clica duas vezes não deveria ver uma falha.
     */
    @Override
    @Transactional
    public void restore(UUID id) {
        Client existing =
                repository
                        .findByIdIncludingDeleted(id)
                        .orElseThrow(() -> NotFoundException.of("Cliente", id));
        if (existing.getDeletedAt() == null) {
            return;
        }
        // Enquanto este cliente esteve excluído, o CPF dele ficou livre - o índice
        // único é parcial de propósito. Se alguém recadastrou a mesma pessoa nesse
        // meio-tempo, trazer o antigo de volta estouraria unique violation no banco:
        // 409 genérico, sem dizer qual campo nem qual cliente está ocupando. Checando
        // aqui, a recusa é 400 nomeando o documento.
        validarIdentidadeUnica(existing, id);
        existing.setDeletedAt(null);
        existing.setUpdatedBy(CurrentUser.id());
        repository.save(existing);
    }

    /**
     * Devolve a senha do INSS e registra QUEM leu e QUANDO.
     *
     * <p>A auditoria é o ponto do endpoint, não um detalhe: a senha é criptografada em repouso, e
     * devolvê-la sem rastro tornaria a criptografia meia medida - protegeria contra quem lê o banco
     * e não contra quem tem login. Com o registro, o escritório consegue responder "quem abriu a
     * senha da dona Maria em março?".
     *
     * <p>Grava em transação própria, como o {@link com.lawfirm.law.firm.audit.AuditLogListener}: o
     * registro da leitura não pode depender de uma transação de escrita que pode nem existir aqui.
     */
    @Override
    @Transactional
    public ClientInssPasswordDTO revealInssPassword(UUID id) {
        Client existing = findOrThrow(id);
        auditRepository.save(
                new AuditLog(
                        "Client",
                        existing.getId(),
                        AuditAction.READ,
                        CurrentUser.id(),
                        "Senha do INSS consultada"));
        return new ClientInssPasswordDTO(existing.getInssPassword());
    }

    @Override
    public Page<ClientSituationHistoryDTO> historyByClientId(
            UUID clientId, int pageNumber, int pageSize) {
        findOrThrow(clientId);
        var pageable = PageRequests.of(pageNumber, pageSize, Sort.by("changedAt").descending());
        return historyRepository.findByClient_Id(clientId, pageable).map(mapper::toHistoryDTO);
    }

    // ── Dados pessoais (aba "Dados pessoais") ──

    @Override
    public ClientPersonalDataResponseDTO getPersonalData(UUID id) {
        return toPersonalDataDTO(findOrThrow(id));
    }

    @Override
    @Transactional
    public ClientPersonalDataResponseDTO updatePersonalData(
            UUID id, ClientPersonalDataRequestDTO dto) {
        Client existing = findOrThrow(id);

        // Antes de escrever: a entidade é gerenciada, e mutar antes de consultar
        // dispara flush automático - aí o índice do banco estoura primeiro e a resposta
        // vira 409 sem dizer o campo. Os três documentos que esta aba não edita vão como
        // estão na entidade.
        validarIdentidadeUnica(
                id,
                dto.getCpf(),
                dto.getRg(),
                existing.getCtps(),
                existing.getNitPis(),
                existing.getBeneficiaryNumber());

        existing.setFullName(dto.getFullName());
        existing.setBirthDate(dto.getBirthDate());
        existing.setCpf(dto.getCpf());
        existing.setRg(dto.getRg());
        existing.setRgIssuer(dto.getRgIssuer());
        existing.setRgIssueDate(dto.getRgIssueDate());
        existing.setMotherName(dto.getMotherName());
        existing.setGender(dto.getGender());
        existing.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getNationality() != null) {
            existing.setNationality(dto.getNationality());
        }
        existing.setMobilePhone(dto.getMobilePhone());
        if (dto.getIsWhatsapp() != null) {
            existing.setIsWhatsapp(dto.getIsWhatsapp());
        }
        existing.setReferencePhone(dto.getReferencePhone());
        existing.setReferenceResponsible(dto.getReferenceResponsible());
        existing.setEmail(dto.getEmail());
        if (dto.getHasDisability() != null) {
            existing.setHasDisability(dto.getHasDisability());
        }
        existing.setUpdatedBy(CurrentUser.id());

        normalizarIdentidade(existing);
        return toPersonalDataDTO(repository.save(existing));
    }

    // ── Dados profissionais (aba "Dados profissionais") ──

    @Override
    public ClientProfessionalDataResponseDTO getProfessionalData(UUID id) {
        return toProfessionalDataDTO(findOrThrow(id));
    }

    @Override
    @Transactional
    public ClientProfessionalDataResponseDTO updateProfessionalData(
            UUID id, ClientProfessionalDataRequestDTO dto) {
        Client existing = findOrThrow(id);

        // Mesma razão da aba de dados pessoais: validar antes de mutar. CPF e RG não são
        // editados aqui e vão como estão na entidade.
        validarIdentidadeUnica(
                id,
                existing.getCpf(),
                existing.getRg(),
                dto.getCtps(),
                dto.getNitPis(),
                dto.getBeneficiaryNumber());

        existing.setProfession(dto.getProfession());
        existing.setNitPis(dto.getNitPis());
        existing.setCtps(dto.getCtps());
        existing.setCtpsSeries(dto.getCtpsSeries());
        existing.setBeneficiaryNumber(dto.getBeneficiaryNumber());
        existing.setContributionTime(dto.getContributionTime());
        existing.setContributionInMonths(
                ContributionTimeParser.toMonths(dto.getContributionTime()));
        // Ausente = mantém: a senha não volta no GET, então quem edita a aba não a tem em mãos.
        if (dto.getInssPassword() != null && !dto.getInssPassword().isBlank()) {
            existing.setInssPassword(dto.getInssPassword());
        }
        existing.setUpdatedBy(CurrentUser.id());

        normalizarIdentidade(existing);
        return toProfessionalDataDTO(repository.save(existing));
    }

    // ── Private helpers ──

    private Client findOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Cliente", id));
    }

    private static Situation parseSituation(String raw) {
        try {
            Situation s = Situation.fromLabel(raw);
            if (s == null) {
                throw new ValidationException("situation", ValidationErrorCode.INVALID_SITUATION);
            }
            return s;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "situation",
                    ValidationErrorCode.INVALID_SITUATION,
                    "Situação inválida: " + raw);
        }
    }

    private static BenefitType parseBenefit(String raw) {
        try {
            BenefitType b = BenefitType.fromLabel(raw);
            if (b == null) {
                throw new ValidationException("benefit", ValidationErrorCode.INVALID_BENEFIT);
            }
            return b;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "benefit", ValidationErrorCode.INVALID_BENEFIT, "Benefício inválido: " + raw);
        }
    }

    private static ClientType parseClientType(String raw) {
        try {
            ClientType t = ClientType.fromLabel(raw);
            if (t == null) {
                throw new ValidationException(
                        "clientType", ValidationErrorCode.INVALID_CLIENT_TYPE);
            }
            return t;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "clientType",
                    ValidationErrorCode.INVALID_CLIENT_TYPE,
                    "Tipo de cliente inválido: " + raw);
        }
    }

    private void recordHistory(Situation previous, Client saved) {
        ClientSituationHistory h = new ClientSituationHistory();
        h.setClient(saved);
        h.setPreviousSituation(previous == null ? null : previous.getLabel());
        h.setNewSituation(saved.getSituation() == null ? null : saved.getSituation().getLabel());
        h.setChangedAt(Instant.now());
        h.setChangedBy(CurrentUser.id());
        historyRepository.save(h);
    }

    /**
     * Normaliza os cinco documentos que identificam o cliente, na entidade, antes de qualquer
     * gravação.
     *
     * <p>Fica aqui, e não no mapper, porque as abas de dados pessoais e profissionais escrevem
     * direto na entidade sem passar por ele - normalizar no mapper deixaria dois dos quatro
     * caminhos de escrita de fora, que é como o defeito nasceu.
     */
    private void normalizarIdentidade(Client entity) {
        entity.setCpf(DocumentoIdentidade.apenasDigitos(entity.getCpf()));
        entity.setRg(DocumentoIdentidade.alfanumericoMaiusculo(entity.getRg()));
        entity.setCtps(DocumentoIdentidade.apenasDigitos(entity.getCtps()));
        entity.setNitPis(DocumentoIdentidade.apenasDigitos(entity.getNitPis()));
        entity.setBeneficiaryNumber(
                DocumentoIdentidade.apenasDigitos(entity.getBeneficiaryNumber()));
    }

    /**
     * Recusa documento que já pertence a outro cliente ativo.
     *
     * <p>Vale para criação E edição. Antes só valia na criação, então o mesmo CPF duplicado dava
     * 400 com o campo apontado no cadastro e 409 genérico na edição - mesma regra, duas respostas,
     * e uma delas sem dizer qual campo.
     *
     * <p>Contato (celular, e-mail, telefone de recado) NÃO entra: contato se compartilha entre mãe
     * e filho, casal, responsável. Tornar único recusaria o segundo cadastro de uma família.
     *
     * @param idAtual id do cliente sendo editado, ou {@code null} na criação. Sem isso, salvar sem
     *     mexer no CPF acusaria duplicidade do registro contra ele mesmo.
     */
    private void validarIdentidadeUnica(Client entity, UUID idAtual) {
        validarIdentidadeUnica(
                idAtual,
                entity.getCpf(),
                entity.getRg(),
                entity.getCtps(),
                entity.getNitPis(),
                entity.getBeneficiaryNumber());
    }

    /**
     * Mesma validação, sobre os valores que ESTÃO PRESTES a ser gravados.
     *
     * <p>Na edição isto tem de rodar ANTES de mexer na entidade. A entidade é gerenciada pelo JPA:
     * assim que os campos mudam, qualquer consulta à mesma tabela dispara um flush automático, e o
     * índice único do banco estoura primeiro - 409 genérico, sem dizer qual campo, que é exatamente
     * o que esta validação existe para evitar. Validar antes de mutar mantém a resposta em 400
     * nomeando o documento.
     */
    private void validarIdentidadeUnica(
            UUID idAtual,
            String cpf,
            String rg,
            String ctps,
            String nitPis,
            String beneficiaryNumber) {
        checarDocumento(
                DocumentoIdentidade.apenasDigitos(cpf),
                idAtual,
                repository::existsByCpf,
                repository::existsByCpfAndIdNot,
                "cpf",
                "CPF já cadastrado para outro cliente");
        checarDocumento(
                DocumentoIdentidade.alfanumericoMaiusculo(rg),
                idAtual,
                repository::existsByRg,
                repository::existsByRgAndIdNot,
                "rg",
                "RG já cadastrado para outro cliente");
        checarDocumento(
                DocumentoIdentidade.apenasDigitos(ctps),
                idAtual,
                repository::existsByCtps,
                repository::existsByCtpsAndIdNot,
                "ctps",
                "CTPS já cadastrada para outro cliente");
        checarDocumento(
                DocumentoIdentidade.apenasDigitos(nitPis),
                idAtual,
                repository::existsByNitPis,
                repository::existsByNitPisAndIdNot,
                "nitPis",
                "NIT/PIS já cadastrado para outro cliente");
        checarDocumento(
                DocumentoIdentidade.apenasDigitos(beneficiaryNumber),
                idAtual,
                repository::existsByBeneficiaryNumber,
                repository::existsByBeneficiaryNumberAndIdNot,
                "beneficiaryNumber",
                "Número do benefício já cadastrado para outro cliente");
    }

    private void checarDocumento(
            String valorNormalizado,
            UUID idAtual,
            Predicate<String> existeNaBase,
            BiPredicate<String, UUID> existeEmOutro,
            String campo,
            String mensagem) {
        if (valorNormalizado == null || valorNormalizado.isBlank()) {
            return;
        }
        boolean duplicado =
                idAtual == null
                        ? existeNaBase.test(valorNormalizado)
                        : existeEmOutro.test(valorNormalizado, idAtual);
        if (duplicado) {
            throw new ValidationException(campo, ValidationErrorCode.DUPLICATE_VALUE, mensagem);
        }
    }

    private static Integer ageOf(LocalDate birthDate) {
        return birthDate == null
                ? null
                : Period.between(birthDate, LocalDate.now(ZoneOffset.UTC)).getYears();
    }

    private ClientPersonalDataResponseDTO toPersonalDataDTO(Client entity) {
        ClientPersonalDataResponseDTO dto = new ClientPersonalDataResponseDTO();
        dto.setClientId(entity.getId());
        dto.setFullName(entity.getFullName());
        dto.setBirthDate(entity.getBirthDate());
        dto.setAge(ageOf(entity.getBirthDate()));
        dto.setCpf(entity.getCpf());
        dto.setRg(entity.getRg());
        dto.setRgIssuer(entity.getRgIssuer());
        dto.setRgIssueDate(entity.getRgIssueDate());
        dto.setMotherName(entity.getMotherName());
        dto.setGender(entity.getGender());
        dto.setMaritalStatus(entity.getMaritalStatus());
        dto.setNationality(entity.getNationality());
        dto.setMobilePhone(entity.getMobilePhone());
        dto.setIsWhatsapp(entity.getIsWhatsapp());
        dto.setReferencePhone(entity.getReferencePhone());
        dto.setReferenceResponsible(entity.getReferenceResponsible());
        dto.setEmail(entity.getEmail());
        dto.setHasDisability(entity.getHasDisability());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ClientProfessionalDataResponseDTO toProfessionalDataDTO(Client entity) {
        ClientProfessionalDataResponseDTO dto = new ClientProfessionalDataResponseDTO();
        dto.setClientId(entity.getId());
        dto.setProfession(entity.getProfession());
        dto.setNitPis(entity.getNitPis());
        dto.setCtps(entity.getCtps());
        dto.setCtpsSeries(entity.getCtpsSeries());
        dto.setContributionTime(entity.getContributionTime());
        dto.setContributionInMonths(entity.getContributionInMonths());
        dto.setBeneficiaryNumber(entity.getBeneficiaryNumber());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
