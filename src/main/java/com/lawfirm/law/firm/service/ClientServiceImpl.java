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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository repository;
    private final ClientMapper mapper;

    public ClientServiceImpl(ClientRepository repository, ClientMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ClientDetailsDTO create(ClientDetailsDTO dto) {
        validateUniqueness(dto);
        Client entity = mapper.toEntity(dto);
        Client saved = repository.save(entity);
        return mapper.toDTO(saved);
    }

    @Override
    public Page<ClientListResponseDTO> listSummary(int page, int size, String searchTerm,
                                                   List<BenefitType> benefitTypes, List<Situation> situations,
                                                   Instant createdFrom, Instant createdTo) {
        var pageable = PageRequest.of(
                Math.max(0, page),
                size <= 0 ? 10 : size,
                Sort.by("id").ascending());

        Specification<Client> spec = ClientSpecification.combine(List.of(
                ClientSpecification.searchTerm(searchTerm),
                ClientSpecification.benefitIn(benefitTypes),
                ClientSpecification.situationIn(situations),
                ClientSpecification.createdBetween(createdFrom, createdTo)
        ));

        Page<Client> result = (spec == null)
                ? repository.findAll(pageable)
                : repository.findAll(spec, pageable);

        return result.map(mapper::toListDTO);
    }

    @Override
    public Optional<ClientDetailsDTO> findById(UUID id) {
        return repository.findById(id).map(mapper::toDTO);
    }

    @Override
    public ClientDetailsDTO patch(UUID id, ClientPatchRequestDTO patch) {
        Client existing = findOrThrow(id);
        if (patch.getSituation() != null) existing.setSituation(patch.getSituation());
        if (patch.getNonBillable() != null) existing.setNonBillable(patch.getNonBillable());
        return mapper.toDTO(repository.save(existing));
    }

    @Override
    public ClientDetailsDTO update(UUID id, ClientDetailsDTO dto) {
        Client existing = findOrThrow(id);

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
        existing.setBenefitNumber(dto.getBenefitNumber());
        existing.setNitPis(dto.getNitPis());
        existing.setProfession(dto.getProfession());
        existing.setCtps(dto.getCtps());
        existing.setCtpsSeries(dto.getCtpsSeries());
        existing.setInssPassword(dto.getInssPassword());
        existing.setContributionTime(dto.getContributionTime());
        existing.setGender(dto.getGender());
        if (dto.getNonBillable() != null) existing.setNonBillable(dto.getNonBillable());

        return mapper.toDTO(repository.save(existing));
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
        checkDuplicate(dto.getBenefitNumber(), repository::existsByBenefitNumberIgnoreCase,
                "benefitNumber", "Número do benefício já cadastrado");
        checkDuplicate(dto.getCpf(), repository::existsByCpf, "cpf", "CPF já cadastrado");
        checkDuplicate(dto.getRg(), repository::existsByRg, "rg", "RG já cadastrado");
        checkDuplicate(dto.getNitPis(), repository::existsByNitPis, "nitPis", "NIT/PIS já cadastrado");
        checkDuplicate(dto.getCtps(), repository::existsByCtps, "ctps", "CTPS já cadastrado");
    }

    private void checkDuplicate(String value, Predicate<String> existsFn,
                                String field, String message) {
        if (value != null && !value.isBlank() && existsFn.test(value.trim())) {
            throw new ValidationException(field, ValidationErrorCode.DUPLICATE_VALUE, message);
        }
    }
}
