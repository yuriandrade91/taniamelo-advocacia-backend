package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Situation;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientService {
    ClientDetailsDTO create(ClientDetailsDTO client);
    Page<ClientListResponseDTO> listSummary(int page, int size, String searchTerm,
                                            List<BenefitType> benefitTypes, List<Situation> situations,
                                            Instant createdFrom, Instant createdTo);
    Optional<ClientDetailsDTO> findById(UUID id);
    ClientDetailsDTO update(UUID id, ClientDetailsDTO client);
    ClientDetailsDTO patch(UUID id, ClientPatchRequestDTO patch);
    void delete(UUID id);
}
