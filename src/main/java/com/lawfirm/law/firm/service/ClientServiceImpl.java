package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientWritableFields;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientSituationHistory;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.ClientSituationHistoryRepository;
import com.lawfirm.law.firm.repository.ClientSpecification;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.ContributionTimeParser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository repository;
    private final ClientMapper mapper;
    private final ClientSituationHistoryRepository historyRepository;

    public ClientServiceImpl(
            ClientRepository repository,
            ClientMapper mapper,
            ClientSituationHistoryRepository historyRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.historyRepository = historyRepository;
    }

    @Override
    @Transactional
    public ClientDetailsDTO create(ClientCreateRequestDTO dto) {
        validateUniqueness(dto);
        Client entity = mapper.toEntity(dto);
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
            Instant createdFrom,
            Instant createdTo) {
        Specification<Client> spec =
                ClientSpecification.combine(
                        List.of(
                                ClientSpecification.searchTerm(searchTerm),
                                ClientSpecification.benefitIn(benefitTypes),
                                ClientSpecification.situationIn(situations),
                                ClientSpecification.createdBetween(createdFrom, createdTo)));

        // updatedAt é sempre populado (prePersist/preUpdate), então ordenar por ele
        // dá "atividade mais recente primeiro" sem query manual.
        var pageable =
                PageRequest.of(
                        Math.max(0, pageNumber - 1),
                        pageSize <= 0 ? 10 : pageSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt"));
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

        mapper.updateEntityFromDto(dto, existing);
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

        if (patch.getNotBillable() != null
                && !Objects.equals(existing.getNotBillable(), patch.getNotBillable())) {
            existing.setNotBillable(patch.getNotBillable());
            existing.setUpdatedBy(CurrentUser.id());
            repository.save(existing);
            notBillableChanged = true;
        }

        return new ClientPatchOutcome(situationChanged, notBillableChanged);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw NotFoundException.of("Cliente", id);
        }
        repository.deleteById(id);
    }

    @Override
    public Page<ClientSituationHistoryDTO> historyByClientId(
            UUID clientId, int pageNumber, int pageSize) {
        findOrThrow(clientId);
        var pageable =
                PageRequest.of(
                        Math.max(0, pageNumber - 1),
                        pageSize <= 0 ? 10 : pageSize,
                        Sort.by("changedAt").descending());
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

        existing.setProfession(dto.getProfession());
        existing.setNitPis(dto.getNitPis());
        existing.setCtps(dto.getCtps());
        existing.setCtpsSeries(dto.getCtpsSeries());
        existing.setBeneficiaryNumber(dto.getBeneficiaryNumber());
        existing.setContributionTime(dto.getContributionTime());
        existing.setContributionInMonths(
                ContributionTimeParser.toMonths(dto.getContributionTime()));
        existing.setInssPassword(dto.getInssPassword());
        existing.setUpdatedBy(CurrentUser.id());

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

    private void recordHistory(Situation previous, Client saved) {
        ClientSituationHistory h = new ClientSituationHistory();
        h.setClient(saved);
        h.setPreviousSituation(previous == null ? null : previous.getLabel());
        h.setNewSituation(saved.getSituation() == null ? null : saved.getSituation().getLabel());
        h.setChangedAt(Instant.now());
        h.setChangedBy(CurrentUser.id());
        historyRepository.save(h);
    }

    private void validateUniqueness(ClientWritableFields dto) {
        checkDuplicate(dto.getCpf(), repository::existsByCpf, "cpf", "CPF já cadastrado");
        checkDuplicate(
                dto.getNitPis(), repository::existsByNitPis, "nitPis", "NIT/PIS já cadastrado");
        checkDuplicate(
                dto.getBeneficiaryNumber(),
                repository::existsByBeneficiaryNumberIgnoreCase,
                "beneficiaryNumber",
                "Número do benefício já cadastrado");
    }

    private void checkDuplicate(
            String value, Predicate<String> existsFn, String field, String message) {
        if (value != null && !value.isBlank() && existsFn.test(value.trim())) {
            throw new ValidationException(field, ValidationErrorCode.DUPLICATE_VALUE, message);
        }
    }

    private static Integer ageOf(LocalDate birthDate) {
        return birthDate == null
                ? null
                : Period.between(birthDate, LocalDate.now(ZoneOffset.UTC)).getYears();
    }

    private ClientPersonalDataResponseDTO toPersonalDataDTO(Client entity) {
        ClientPersonalDataResponseDTO dto = new ClientPersonalDataResponseDTO();
        dto.setId(entity.getId());
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
        dto.setId(entity.getId());
        dto.setProfession(entity.getProfession());
        dto.setNitPis(entity.getNitPis());
        dto.setCtps(entity.getCtps());
        dto.setCtpsSeries(entity.getCtpsSeries());
        dto.setContributionTime(entity.getContributionTime());
        dto.setContributionInMonths(entity.getContributionInMonths());
        dto.setBeneficiaryNumber(entity.getBeneficiaryNumber());
        dto.setInssPassword(entity.getInssPassword());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
