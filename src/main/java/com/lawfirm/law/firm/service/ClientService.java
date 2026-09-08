package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientPatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Situation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface ClientService {

    ClientDetailsDTO create(ClientCreateRequestDTO dto);

    Page<ClientListResponseDTO> listSummary(
            int pageNumber,
            int pageSize,
            String searchTerm,
            List<BenefitType> benefitTypes,
            List<Situation> situations,
            List<ClientType> clientTypes,
            Instant createdFrom,
            Instant createdTo);

    Optional<ClientDetailsDTO> findById(UUID id);

    ClientDetailsDTO update(UUID id, ClientUpdateRequestDTO dto);

    /** Atualização parcial (situação/arrecadação). Retorna o que efetivamente mudou. */
    ClientPatchOutcome patch(UUID id, ClientPatchRequestDTO patch);

    /**
     * Exclusão lógica: marca {@code deletedAt} e some das consultas. Reversível por {@link
     * #restore}.
     */
    void delete(UUID id);

    /** Desfaz a exclusão lógica. Idempotente em cliente já ativo. */
    void restore(UUID id);

    Page<ClientSituationHistoryDTO> historyByClientId(UUID clientId, int pageNumber, int pageSize);

    ClientPersonalDataResponseDTO getPersonalData(UUID id);

    ClientPersonalDataResponseDTO updatePersonalData(UUID id, ClientPersonalDataRequestDTO dto);

    ClientProfessionalDataResponseDTO getProfessionalData(UUID id);

    ClientProfessionalDataResponseDTO updateProfessionalData(
            UUID id, ClientProfessionalDataRequestDTO dto);
}
