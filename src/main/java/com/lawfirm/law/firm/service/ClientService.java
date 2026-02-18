package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface ClientService {
    ClientDetailsDTO create(ClientDetailsDTO client);
    Page<ClientDetailsDTO> listAll(int page, int size);
    Page<ClientDetailsDTO> listAll(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations);
    Page<ClientDetailsDTO> listAll(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations, java.time.Instant createdFrom, java.time.Instant createdTo);
    // summary/list DTO variants
    org.springframework.data.domain.Page<com.lawfirm.law.firm.dto.ClientListResponseDTO> listSummary(int page, int size, String searchTerm, List<BenefitType> benefitTypes, List<Situation> situations, java.time.Instant createdFrom, java.time.Instant createdTo);
    java.util.Optional<ClientDetailsDTO> findById(UUID id);
    ClientDetailsDTO update(UUID id, ClientDetailsDTO client);
    ClientDetailsDTO patch(UUID id, com.lawfirm.law.firm.dto.ClientPatchRequestDTO patch);
    void delete(UUID id);
}
