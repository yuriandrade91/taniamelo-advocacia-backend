package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientInterviewRequestDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientInterview;
import com.lawfirm.law.firm.repository.ClientInterviewRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrevistas/atendimentos do cliente (data, duração, conteúdo rich text). */
@Service
public class ClientInterviewService {

    private final ClientInterviewRepository repository;
    private final ClientRepository clientRepository;

    public ClientInterviewService(
            ClientInterviewRepository repository, ClientRepository clientRepository) {
        this.repository = repository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientInterviewResponseDTO create(UUID clientId, ClientInterviewRequestDTO dto) {
        Client client = findClientOrThrow(clientId);

        ClientInterview entity = new ClientInterview();
        entity.setClient(client);
        entity.setContent(dto.getContent());
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setOccurredAt(dto.getOccurredAt() != null ? dto.getOccurredAt() : Instant.now());
        entity.setCreatedBy(CurrentUser.id());

        return toDTO(repository.save(entity));
    }

    public Page<ClientInterviewResponseDTO> list(UUID clientId, int pageNumber, int pageSize) {
        findClientOrThrow(clientId);
        var pageable =
                PageRequest.of(
                        Math.max(0, pageNumber - 1),
                        pageSize <= 0 ? 10 : pageSize,
                        Sort.by(Sort.Direction.DESC, "occurredAt"));
        return repository.findByClient_IdAndDeletedAtIsNull(clientId, pageable).map(this::toDTO);
    }

    public ClientInterviewResponseDTO get(UUID clientId, UUID interviewId) {
        return toDTO(findInterviewOrThrow(clientId, interviewId));
    }

    @Transactional
    public ClientInterviewResponseDTO update(
            UUID clientId, UUID interviewId, ClientInterviewRequestDTO dto) {
        ClientInterview entity = findInterviewOrThrow(clientId, interviewId);
        entity.setContent(dto.getContent());
        if (dto.getOccurredAt() != null) {
            entity.setOccurredAt(dto.getOccurredAt());
        }
        if (dto.getDurationMinutes() != null) {
            entity.setDurationMinutes(dto.getDurationMinutes());
        }
        entity.setUpdatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID clientId, UUID interviewId) {
        ClientInterview entity = findInterviewOrThrow(clientId, interviewId);
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(CurrentUser.id());
        repository.save(entity);
    }

    // ── Private helpers ──

    private Client findClientOrThrow(UUID clientId) {
        return clientRepository
                .findById(clientId)
                .orElseThrow(() -> NotFoundException.of("Cliente", clientId));
    }

    private ClientInterview findInterviewOrThrow(UUID clientId, UUID interviewId) {
        findClientOrThrow(clientId);
        return repository
                .findByIdAndClient_IdAndDeletedAtIsNull(interviewId, clientId)
                .orElseThrow(() -> NotFoundException.of("Entrevista", interviewId));
    }

    private ClientInterviewResponseDTO toDTO(ClientInterview entity) {
        ClientInterviewResponseDTO dto = new ClientInterviewResponseDTO();
        dto.setId(entity.getId());
        dto.setOccurredAt(entity.getOccurredAt());
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setContent(entity.getContent());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
