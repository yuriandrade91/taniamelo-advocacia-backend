package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientMapper;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClientServiceImpl implements ClientService {
    private final ClientRepository repository;
    private final ClientMapper mapper;

    public ClientServiceImpl(ClientRepository repository, ClientMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ClientDetailsDTO create(ClientDetailsDTO clientResponseDto) {
        // Pre-validate unique constraints to return friendly messages
        if (clientResponseDto.getBenefitNumber() != null && !clientResponseDto.getBenefitNumber().isBlank()) {
            String normalized = clientResponseDto.getBenefitNumber().trim().toUpperCase();
            // prefer case-insensitive existence check
            if (repository.existsByBenefitNumberIgnoreCase(normalized)) {
                throw new ValidationException("benefitNumber", ValidationErrorCode.DUPLICATE_VALUE, "Número do benefício já cadastrado");
            }
            clientResponseDto.setBenefitNumber(normalized);
        }
        if (clientResponseDto.getCpf() != null && !clientResponseDto.getCpf().isBlank()) {
            if (repository.existsByCpf(clientResponseDto.getCpf())) {
                throw new ValidationException("cpf", ValidationErrorCode.DUPLICATE_VALUE, "CPF já cadastrado");
            }
        }
        if (clientResponseDto.getRg() != null && !clientResponseDto.getRg().isBlank()) {
            if (repository.existsByRg(clientResponseDto.getRg())) {
                throw new ValidationException("rg", ValidationErrorCode.DUPLICATE_VALUE, "RG já cadastrado");
            }
        }
        if (clientResponseDto.getNitPis() != null && !clientResponseDto.getNitPis().isBlank()) {
            if (repository.existsByNitPis(clientResponseDto.getNitPis())) {
                throw new ValidationException("nitPis", ValidationErrorCode.DUPLICATE_VALUE, "NIT/PIS já cadastrado");
            }
        }
        if (clientResponseDto.getCtps() != null && !clientResponseDto.getCtps().isBlank()) {
            if (repository.existsByCtps(clientResponseDto.getCtps())) {
                throw new ValidationException("ctps", ValidationErrorCode.DUPLICATE_VALUE, "CTPS já cadastrado");
            }
        }

        Client entity = mapper.toEntity(clientResponseDto);
        // ensure createdAt will be set by @PrePersist if null
        Client saved = repository.save(entity);
        return mapper.toDTO(saved);
    }

    @Override
    public Page<ClientDetailsDTO> listAll(int page, int size) {
    int pageIndex = Math.max(0, page);
    int pageSize = size <= 0 ? 10 : size;
    var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());
    Page<Client> pageRes = repository.findAll(pageable);
    // Use Page.map to preserve paging metadata
    return pageRes.map(mapper::toDTO);
    }

    @Override
    public Page<ClientDetailsDTO> listAll(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations) {
    int pageIndex = Math.max(0, page);
    int pageSize = size <= 0 ? 10 : size;
    var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());

        // Build specifications
        List<Specification<Client>> specs = new ArrayList<>();
        specs.add(ClientSpecification.searchTerm(searchTerm));

        specs.add(ClientSpecification.benefitIn(benefitTypes == null ? List.of() : benefitTypes));
        specs.add(ClientSpecification.situationIn(situations == null ? List.of() : situations));

        Specification<Client> combined = ClientSpecification.combine(specs);

    Page<Client> pageRes = (combined == null) ? repository.findAll(pageable) : repository.findAll(combined, pageable);
    return pageRes.map(mapper::toDTO);
    }

    @Override
    public Page<ClientDetailsDTO> listAll(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations, java.time.Instant createdFrom, java.time.Instant createdTo) {
        int pageIndex = Math.max(0, page);
        int pageSize = size <= 0 ? 10 : size;
        var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());

        // Build specifications
        List<Specification<Client>> specs = new ArrayList<>();
        specs.add(ClientSpecification.searchTerm(searchTerm));
        specs.add(ClientSpecification.benefitIn(benefitTypes == null ? List.of() : benefitTypes));
        specs.add(ClientSpecification.situationIn(situations == null ? List.of() : situations));
        specs.add(ClientSpecification.createdBetween(createdFrom, createdTo));

        Specification<Client> combined = ClientSpecification.combine(specs);

        Page<Client> pageRes = (combined == null) ? repository.findAll(pageable) : repository.findAll(combined, pageable);
        return pageRes.map(mapper::toDTO);
    }

    @Override
    public org.springframework.data.domain.Page<com.lawfirm.law.firm.dto.ClientListResponseDTO> listSummary(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations, java.time.Instant createdFrom, java.time.Instant createdTo) {
        return listSummary(page, size, searchTerm, benefitTypes, situations, createdFrom, createdTo, null);
    }

    public org.springframework.data.domain.Page<com.lawfirm.law.firm.dto.ClientListResponseDTO> listSummary(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations, java.time.Instant createdFrom, java.time.Instant createdTo, Boolean nonBillable) {
        int pageIndex = Math.max(0, page);
        int pageSize = size <= 0 ? 10 : size;
        var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());

        // Build specifications
        List<Specification<Client>> specs = new ArrayList<>();
        specs.add(ClientSpecification.searchTerm(searchTerm));
        specs.add(ClientSpecification.benefitIn(benefitTypes == null ? List.of() : benefitTypes));
        specs.add(ClientSpecification.situationIn(situations == null ? List.of() : situations));
        specs.add(ClientSpecification.createdBetween(createdFrom, createdTo));

        Specification<Client> combined = ClientSpecification.combine(specs);

        Page<Client> pageRes = (combined == null) ? repository.findAll(pageable) : repository.findAll(combined, pageable);
        return pageRes.map(mapper::toListDTO);
    }

    @Override
    public Optional<ClientDetailsDTO> findById(UUID id) {
        return repository.findById(id).map(mapper::toDTO);
    }

    @Override
    public ClientDetailsDTO patch(UUID id, com.lawfirm.law.firm.dto.ClientPatchRequestDTO patch) {
        return repository.findById(id).map(existing -> {
            if (patch.getSituation() != null) {
                existing.setSituation(patch.getSituation());
            }
            if (patch.getNonBillable() != null) {
                existing.setNonBillable(patch.getNonBillable());
            }
            Client saved = repository.save(existing);
            return mapper.toDTO(saved);
        }).orElseThrow(() -> new NotFoundException("Client not found with id: " + id));
    }

    @Override
    public ClientDetailsDTO update(UUID id, ClientDetailsDTO clientResponseDto) {
        return repository.findById(id).map(existing -> {
            // update fields from DTO
            existing.setFullName(clientResponseDto.getFullName());
            existing.setFirstName(clientResponseDto.getFirstName());
            existing.setLastName(clientResponseDto.getLastName());
            existing.setBirthDate(clientResponseDto.getBirthDate());
            existing.setMaritalStatus(clientResponseDto.getMaritalStatus());
            existing.setCpf(clientResponseDto.getCpf());
            existing.setRg(clientResponseDto.getRg());
            existing.setMotherName(clientResponseDto.getMotherName());
            existing.setEmail(clientResponseDto.getEmail());
            existing.setMobilePhone(clientResponseDto.getMobilePhone());
            existing.setReferencePhone(clientResponseDto.getReferencePhone());
            existing.setReferenceResponsible(clientResponseDto.getReferenceResponsible());
            existing.setBenefit(clientResponseDto.getBenefit());
            existing.setSituation(clientResponseDto.getSituation());
            existing.setBenefitNumber(clientResponseDto.getBenefitNumber());
            existing.setNitPis(clientResponseDto.getNitPis());
            existing.setProfession(clientResponseDto.getProfession());
            existing.setCtps(clientResponseDto.getCtps());
            existing.setCtpsSeries(clientResponseDto.getCtpsSeries());
            existing.setInssPassword(clientResponseDto.getInssPassword());
            existing.setContributionTime(clientResponseDto.getContributionTime());
            existing.setNonBillable(clientResponseDto.getNonBillable() == null ? existing.getNonBillable() : clientResponseDto.getNonBillable());
            existing.setGender(clientResponseDto.getGender());

            Client updated = repository.save(existing);
            return mapper.toDTO(updated);
        }).orElseThrow(() -> new NotFoundException("Client not found with id: " + id));
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Client not found with id: " + id);
        }
        repository.deleteById(id);
    }
}
