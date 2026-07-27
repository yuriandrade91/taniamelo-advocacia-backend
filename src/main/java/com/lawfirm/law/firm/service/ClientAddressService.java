package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientAddressRequestDTO;
import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.AddressType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientAddress;
import com.lawfirm.law.firm.repository.ClientAddressRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de endereços do cliente (1:N - residencial/comercial/correspondência). Fonte única de
 * endereço do cliente (o endereço embutido em `clients` foi removido do modelo). Garante, no banco
 * e na aplicação, exatamente um endereço "principal" por cliente enquanto houver algum cadastrado.
 */
@Service
public class ClientAddressService {

    private final ClientAddressRepository repository;
    private final ClientRepository clientRepository;

    public ClientAddressService(
            ClientAddressRepository repository, ClientRepository clientRepository) {
        this.repository = repository;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientAddressResponseDTO create(UUID clientId, ClientAddressRequestDTO dto) {
        Client client = findClientOrThrow(clientId);

        ClientAddress entity = new ClientAddress();
        entity.setClient(client);
        applyFields(entity, dto);
        entity.setCreatedBy(CurrentUser.id());

        boolean isFirstAddress = repository.countByClient_Id(clientId) == 0;
        boolean wantsPrimary = Boolean.TRUE.equals(dto.getIsPrimary()) || isFirstAddress;

        if (wantsPrimary) {
            unsetCurrentPrimary(clientId);
            entity.setIsPrimary(true);
        } else {
            entity.setIsPrimary(false);
        }

        return toDTO(repository.save(entity));
    }

    public org.springframework.data.domain.Page<ClientAddressResponseDTO> list(
            UUID clientId, int pageNumber, int pageSize) {
        findClientOrThrow(clientId);
        var pageable =
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, pageNumber - 1),
                        pageSize <= 0 ? 10 : pageSize,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("isPrimary"),
                                org.springframework.data.domain.Sort.Order.asc("createdAt")));
        return repository.findByClient_Id(clientId, pageable).map(this::toDTO);
    }

    public ClientAddressResponseDTO get(UUID clientId, UUID addressId) {
        return toDTO(findAddressOrThrow(clientId, addressId));
    }

    @Transactional
    public ClientAddressResponseDTO update(
            UUID clientId, UUID addressId, ClientAddressRequestDTO dto) {
        ClientAddress entity = findAddressOrThrow(clientId, addressId);
        applyFields(entity, dto);
        entity.setUpdatedBy(CurrentUser.id());

        boolean isOnlyAddress = repository.countByClient_Id(clientId) == 1;
        boolean wantsPrimary = Boolean.TRUE.equals(dto.getIsPrimary()) || isOnlyAddress;

        if (wantsPrimary && !Boolean.TRUE.equals(entity.getIsPrimary())) {
            unsetCurrentPrimary(clientId);
            entity.setIsPrimary(true);
        } else if (!wantsPrimary) {
            entity.setIsPrimary(false);
        }

        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID clientId, UUID addressId) {
        ClientAddress entity = findAddressOrThrow(clientId, addressId);
        boolean wasPrimary = Boolean.TRUE.equals(entity.getIsPrimary());
        repository.delete(entity);

        if (wasPrimary) {
            // Promove o endereço restante mais antigo a principal, para nunca deixar
            // o cliente sem um endereço principal enquanto tiver algum cadastrado.
            repository.findByClient_IdOrderByIsPrimaryDescCreatedAtAsc(clientId).stream()
                    .findFirst()
                    .ifPresent(
                            remaining -> {
                                remaining.setIsPrimary(true);
                                remaining.setUpdatedBy(CurrentUser.id());
                                repository.save(remaining);
                            });
        }
    }

    // ── Private helpers ──

    private void applyFields(ClientAddress entity, ClientAddressRequestDTO dto) {
        entity.setAddressType(parseAddressType(dto.getAddressType()));
        entity.setStreet(dto.getStreet());
        entity.setAddressNumber(dto.getAddressNumber());
        entity.setComplement(dto.getComplement());
        entity.setNeighborhood(dto.getNeighborhood());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setZipCode(dto.getZipCode());
    }

    /**
     * Desmarca a principal atual (se houver), com flush imediato para respeitar o índice único
     * parcial.
     */
    private void unsetCurrentPrimary(UUID clientId) {
        repository
                .findByClient_IdAndIsPrimaryTrue(clientId)
                .ifPresent(
                        previous -> {
                            previous.setIsPrimary(false);
                            previous.setUpdatedBy(CurrentUser.id());
                            repository.saveAndFlush(previous);
                        });
    }

    private Client findClientOrThrow(UUID clientId) {
        return ClientLookup.orThrow(clientRepository, clientId);
    }

    private ClientAddress findAddressOrThrow(UUID clientId, UUID addressId) {
        findClientOrThrow(clientId);
        return repository
                .findByIdAndClient_Id(addressId, clientId)
                .orElseThrow(() -> NotFoundException.of("Endereço", addressId));
    }

    private AddressType parseAddressType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AddressType.RESIDENCIAL;
        }
        try {
            return AddressType.fromLabel(raw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "addressType",
                    ValidationErrorCode.INVALID_ENUM_VALUE,
                    "Tipo de endereço inválido: " + raw);
        }
    }

    private ClientAddressResponseDTO toDTO(ClientAddress entity) {
        ClientAddressResponseDTO dto = new ClientAddressResponseDTO();
        dto.setId(entity.getId());
        dto.setAddressType(
                entity.getAddressType() != null ? entity.getAddressType().getLabel() : null);
        dto.setStreet(entity.getStreet());
        dto.setAddressNumber(entity.getAddressNumber());
        dto.setComplement(entity.getComplement());
        dto.setNeighborhood(entity.getNeighborhood());
        dto.setCity(entity.getCity());
        dto.setState(entity.getState());
        dto.setZipCode(entity.getZipCode());
        dto.setIsPrimary(entity.getIsPrimary());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
