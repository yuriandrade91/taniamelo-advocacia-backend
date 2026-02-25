package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.ClientSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
 

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository repository;
    private final ClientMapper mapper;
    private final com.lawfirm.law.firm.repository.ClientSituationHistoryRepository historyRepository;
    private final EntityManager em;

    public ClientServiceImpl(ClientRepository repository, ClientMapper mapper,
                             com.lawfirm.law.firm.repository.ClientSituationHistoryRepository historyRepository,
                             EntityManager em) {
        this.repository = repository;
        this.mapper = mapper;
        this.historyRepository = historyRepository;
        this.em = em;
    }

    @Override
    public ClientDetailsDTO create(ClientDetailsDTO dto) {
        validateUniqueness(dto);
        Client entity = mapper.toEntity(dto);
        Client saved = repository.save(entity);
    // record initial situation entry
    recordHistory(null, saved);
        return mapper.toDTO(saved);
    }

    @Override
    public Page<ClientListResponseDTO> listSummary(int page, int size, String searchTerm,
                                                   List<BenefitType> benefitTypes, List<Situation> situations,
                                                   Instant createdFrom, Instant createdTo) {
    int pageIndex = Math.max(0, page);
    int pageSize = size <= 0 ? 10 : size;

    Specification<Client> spec = ClientSpecification.combine(List.of(
        ClientSpecification.searchTerm(searchTerm),
        ClientSpecification.benefitIn(benefitTypes),
        ClientSpecification.situationIn(situations),
        ClientSpecification.createdBetween(createdFrom, createdTo)
    ));

    // Build criteria query to order by GREATEST(updatedAt, createdAt) desc
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Client> cq = cb.createQuery(Client.class);
    Root<Client> root = cq.from(Client.class);

    Predicate predicate = null;
    if (spec != null) {
        predicate = spec.toPredicate(root, cq, cb);
    }
    if (predicate != null) cq.where(predicate);

    // Order by the most recent timestamp between updatedAt and createdAt.
    // Use a CASE expression instead of SQL GREATEST to ensure a concrete Java type (Instant)
    Expression<java.time.Instant> mostRecent = cb.coalesce(
        root.get("updatedAt").as(java.time.Instant.class),
        root.get("createdAt").as(java.time.Instant.class)
    );
    cq.orderBy(cb.desc(mostRecent));

    TypedQuery<Client> query = em.createQuery(cq);
    query.setFirstResult(pageIndex * pageSize);
    query.setMaxResults(pageSize);
    var results = query.getResultList();

    // count
    CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
    Root<Client> countRoot = countQ.from(Client.class);
    countQ.select(cb.count(countRoot));
    if (spec != null) {
        Predicate countPred = spec.toPredicate(countRoot, countQ, cb);
        if (countPred != null) countQ.where(countPred);
    }
    Long total = em.createQuery(countQ).getSingleResult();

    java.util.List<ClientListResponseDTO> dtos = results.stream().map(mapper::toListDTO).toList();
    return new org.springframework.data.domain.PageImpl<>(dtos, PageRequest.of(pageIndex, pageSize), total);
    }

    @Override
    public Optional<ClientDetailsDTO> findById(UUID id) {
        return repository.findById(id).map(mapper::toDTO);
    }

    @Override
    public ClientDetailsDTO patch(UUID id, ClientPatchRequestDTO patch) {
    Client existing = findOrThrow(id);
        if (patch.getSituation() != null) {
            // convert incoming string to Situation (accept enum name, label or normalized)
            com.lawfirm.law.firm.model.Situation incoming = null;
            String raw = patch.getSituation();
            if (raw != null) {
                String candidate = raw.trim().toUpperCase().replaceAll("\\s+", "_").replaceAll("[^A-Z0-9_]", "");
                // try name match
                for (com.lawfirm.law.firm.model.Situation s : com.lawfirm.law.firm.model.Situation.values()) {
                    if (s.name().equals(candidate) || s.name().equalsIgnoreCase(candidate)) { incoming = s; break; }
                }
                // try case-insensitive name
                if (incoming == null) {
                    for (com.lawfirm.law.firm.model.Situation s : com.lawfirm.law.firm.model.Situation.values()) {
                        if (s.name().equalsIgnoreCase(raw.trim())) { incoming = s; break; }
                    }
                }
                // try label match
                if (incoming == null) {
                    for (com.lawfirm.law.firm.model.Situation s : com.lawfirm.law.firm.model.Situation.values()) {
                        if (s.getLabel().equalsIgnoreCase(raw.trim())) { incoming = s; break; }
                    }
                }
                // try normalized
                if (incoming == null) {
                    String norm = com.lawfirm.law.firm.model.Situation.normalizeForComparison(raw);
                    for (com.lawfirm.law.firm.model.Situation s : com.lawfirm.law.firm.model.Situation.values()) {
                        if (com.lawfirm.law.firm.model.Situation.normalizeForComparison(s.name()).equals(norm)
                                || com.lawfirm.law.firm.model.Situation.normalizeForComparison(s.getLabel()).equals(norm)) {
                            incoming = s; break;
                        }
                    }
                }
            }
            if (incoming == null) {
                throw new com.lawfirm.law.firm.exception.ValidationException(null, com.lawfirm.law.firm.exception.ValidationErrorCode.INVALID_SITUATION, "Unknown situation: " + patch.getSituation());
            }
            Situation previous = existing.getSituation();
            existing.setSituation(incoming);
            Client saved = repository.save(existing);
            if (!Objects.equals(previous, saved.getSituation())) {
                recordHistory(previous, saved);
            }
            return mapper.toDTO(saved);
        }
        if (patch.getNonBillable() != null) existing.setNonBillable(patch.getNonBillable());
        return mapper.toDTO(repository.save(existing));
    }

    @Override
    public ClientDetailsDTO update(UUID id, ClientDetailsDTO dto) {
        Client existing = findOrThrow(id);
    Situation previous = existing.getSituation();
        existing.setFullName(dto.getFullName());
        existing.setBirthDate(dto.getBirthDate());
        existing.setMaritalStatus(dto.getMaritalStatus());
        existing.setCpf(dto.getCpf());
        existing.setRg(dto.getRg());
        existing.setMotherName(dto.getMotherName());
        existing.setEmail(dto.getEmail());
        existing.setMobilePhone(dto.getMobilePhone());
        existing.setReferencePhone(dto.getReferencePhone());
        existing.setReferenceResponsible(dto.getReferenceResponsible());
        existing.setBenefit(dto.getBenefit());
        existing.setSituation(dto.getSituation());
    existing.setBeneficiaryNumber(dto.getBeneficiaryNumber());
        existing.setNitPis(dto.getNitPis());
        existing.setProfession(dto.getProfession());
        existing.setCtps(dto.getCtps());
        existing.setCtpsSeries(dto.getCtpsSeries());
        existing.setInssPassword(dto.getInssPassword());
        existing.setContributionTime(dto.getContributionTime());
        existing.setGender(dto.getGender());
        if (dto.getNonBillable() != null) existing.setNonBillable(dto.getNonBillable());

        Client saved = repository.save(existing);
    // if situation changed, record
    if (!Objects.equals(previous, saved.getSituation())) {
            recordHistory(previous, saved);
        }
        return mapper.toDTO(saved);
    }

    @Override
    public java.util.List<com.lawfirm.law.firm.dto.ClientSituationHistoryDTO> historyByClientId(UUID clientId) {
    return historyRepository.findByClient_IdOrderByChangedAtDesc(clientId).stream()
        .map(mapper::toHistoryDTO)
        .toList();
    }

    @Override
    public org.springframework.data.domain.Page<com.lawfirm.law.firm.dto.ClientSituationHistoryDTO> historyByClientId(UUID clientId, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), size <= 0 ? 10 : size,
                org.springframework.data.domain.Sort.by("changedAt").descending());
        var result = historyRepository.findByClient_Id(clientId, pageable);
        return result.map(mapper::toHistoryDTO);
    }

    @org.springframework.transaction.annotation.Transactional
    private void recordHistory(Situation previous, Client saved) {
        com.lawfirm.law.firm.model.ClientSituationHistory h = new com.lawfirm.law.firm.model.ClientSituationHistory();
        h.setClient(saved);
    h.setPreviousSituation(previous == null ? null : previous.getLabel());
    h.setNewSituation(saved.getSituation() == null ? null : saved.getSituation().getLabel());
        h.setChangedAt(java.time.Instant.now());
        // changedBy not available in current context; leave null
        historyRepository.save(h);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Client not found with id: " + id);
        }
        repository.deleteById(id);
    }

    // ── Private helpers ──

    private Client findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client not found with id: " + id));
    }

    private void validateUniqueness(ClientDetailsDTO dto) {
    checkDuplicate(dto.getBeneficiaryNumber(), repository::existsByBeneficiaryNumberIgnoreCase,
    "beneficiaryNumber", "Número do benefício já cadastrado");
        checkDuplicate(dto.getCpf(), repository::existsByCpf, "cpf", "CPF já cadastrado");
        checkDuplicate(dto.getRg(), repository::existsByRg, "rg", "RG já cadastrado");
        checkDuplicate(dto.getNitPis(), repository::existsByNitPis, "nitPis", "NIT/PIS já cadastrado");
        checkDuplicate(dto.getCtps(), repository::existsByCtps, "ctps", "CTPS já cadastrado");
    }

    private void checkDuplicate(String value, java.util.function.Predicate<String> existsFn,
                                String field, String message) {
        if (value != null && !value.isBlank() && existsFn.test(value.trim())) {
            throw new ValidationException(field, ValidationErrorCode.DUPLICATE_VALUE, message);
        }
    }
}
