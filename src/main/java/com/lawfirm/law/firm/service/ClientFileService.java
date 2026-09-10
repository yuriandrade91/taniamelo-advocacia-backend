package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUploadMetadataDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationUploadMetadataDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientFile;
import com.lawfirm.law.firm.model.DocumentType;
import com.lawfirm.law.firm.model.FileKind;
import com.lawfirm.law.firm.repository.ClientFileRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.storage.AllowedMimeTypes;
import com.lawfirm.law.firm.storage.FileDownload;
import com.lawfirm.law.firm.storage.FileStorageService;
import com.lawfirm.law.firm.storage.LoadedFile;
import com.lawfirm.law.firm.storage.StoredFile;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Collection única de arquivos do cliente (client_files), servindo as abas "Documentos" (11 tipos)
 * e "Simulações" (versão, vínculos, principal).
 *
 * <p>Regras de "principal" (só para simulações): - a simulação mais recente enviada vira a
 * principal automaticamente; - o usuário pode trocar a principal via markSimulationPrincipal; - ao
 * excluir a principal, a mais recente restante é promovida.
 */
@Service
public class ClientFileService {

    private final ClientFileRepository repository;
    private final ClientRepository clientRepository;
    private final FileStorageService fileStorageService;

    public ClientFileService(
            ClientFileRepository repository,
            ClientRepository clientRepository,
            FileStorageService fileStorageService) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.fileStorageService = fileStorageService;
    }

    // ── Documentos ──

    @Transactional
    public List<ClientFileDocumentResponseDTO> uploadDocuments(
            UUID clientId,
            List<MultipartFile> files,
            List<ClientFileDocumentUploadMetadataDTO> metadata) {
        Client client = findClientOrThrow(clientId);
        validateBatch(files, metadata == null ? -1 : metadata.size());

        List<ClientFileDocumentResponseDTO> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            ClientFileDocumentUploadMetadataDTO meta = metadata.get(i);

            DocumentType type = parseDocumentType(meta.getDocumentType(), true);
            validateMimeType(
                    file,
                    AllowedMimeTypes.DOCUMENTS,
                    "Tipo de arquivo não permitido (aceitos: PDF, PNG, JPEG)");

            ClientFile entity =
                    newFile(client, FileKind.DOCUMENT, file, "clients/" + clientId + "/documents");
            entity.setDocumentType(type);
            entity.setNotes(meta.getNotes());

            result.add(toDocumentDTO(repository.save(entity)));
        }
        return result;
    }

    public Page<ClientFileDocumentResponseDTO> listDocuments(
            UUID clientId, String documentType, int pageNumber, int pageSize) {
        findClientOrThrow(clientId);
        Pageable pageable = pageable(pageNumber, pageSize);
        DocumentType type = parseDocumentType(documentType, false);
        Page<ClientFile> page =
                (type == null)
                        ? repository.findByClient_IdAndKindAndDeletedAtIsNull(
                                clientId, FileKind.DOCUMENT, pageable)
                        : repository.findByClient_IdAndKindAndDocumentTypeAndDeletedAtIsNull(
                                clientId, FileKind.DOCUMENT, type, pageable);
        return page.map(this::toDocumentDTO);
    }

    public ClientFileDocumentResponseDTO getDocument(UUID clientId, UUID fileId) {
        return toDocumentDTO(findFileOrThrow(clientId, fileId, FileKind.DOCUMENT));
    }

    @Transactional
    public ClientFileDocumentResponseDTO updateDocument(
            UUID clientId, UUID fileId, ClientFileDocumentUpdateRequestDTO dto) {
        ClientFile file = findFileOrThrow(clientId, fileId, FileKind.DOCUMENT);
        if (dto.getDocumentType() != null) {
            file.setDocumentType(parseDocumentType(dto.getDocumentType(), true));
        }
        if (dto.getNotes() != null) {
            file.setNotes(dto.getNotes());
        }
        file.setUpdatedBy(CurrentUser.id());
        return toDocumentDTO(repository.save(file));
    }

    // ── Simulações ──

    @Transactional
    public List<ClientFileSimulationResponseDTO> uploadSimulations(
            UUID clientId,
            List<MultipartFile> files,
            List<ClientFileSimulationUploadMetadataDTO> metadata) {
        Client client = findClientOrThrow(clientId);
        validateBatch(files, metadata == null ? -1 : metadata.size());

        List<ClientFileSimulationResponseDTO> result = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            ClientFileSimulationUploadMetadataDTO meta = metadata.get(i);

            validateMimeType(file, AllowedMimeTypes.SIMULATIONS, "Simulação só aceita PDF");

            ClientFile entity =
                    newFile(
                            client,
                            FileKind.SIMULATION,
                            file,
                            "clients/" + clientId + "/simulations");
            entity.setSimulationDate(meta.getSimulationDate());
            entity.setVersion(meta.getVersion());
            entity.setVinculos(meta.getVinculos());
            entity.setNotes(meta.getNotes());

            // Por padrão, a simulação mais recente é a principal.
            unsetCurrentPrincipal(clientId);
            entity.setIsPrincipal(true);

            result.add(toSimulationDTO(repository.save(entity)));
        }
        return result;
    }

    public Page<ClientFileSimulationResponseDTO> listSimulations(
            UUID clientId, int pageNumber, int pageSize) {
        findClientOrThrow(clientId);
        return repository
                .findByClient_IdAndKindAndDeletedAtIsNull(
                        clientId, FileKind.SIMULATION, pageable(pageNumber, pageSize))
                .map(this::toSimulationDTO);
    }

    public ClientFileSimulationResponseDTO getSimulation(UUID clientId, UUID fileId) {
        return toSimulationDTO(findFileOrThrow(clientId, fileId, FileKind.SIMULATION));
    }

    @Transactional
    public ClientFileSimulationResponseDTO updateSimulation(
            UUID clientId, UUID fileId, ClientFileSimulationUpdateRequestDTO dto) {
        ClientFile file = findFileOrThrow(clientId, fileId, FileKind.SIMULATION);
        if (dto.getSimulationDate() != null) {
            file.setSimulationDate(dto.getSimulationDate());
        }
        if (dto.getVersion() != null) {
            file.setVersion(dto.getVersion());
        }
        if (dto.getVinculos() != null) {
            file.setVinculos(dto.getVinculos());
        }
        if (dto.getNotes() != null) {
            file.setNotes(dto.getNotes());
        }
        file.setUpdatedBy(CurrentUser.id());
        return toSimulationDTO(repository.save(file));
    }

    /**
     * Marca esta simulação como principal, desmarcando a anterior na mesma transação (flush
     * explícito antes, para o índice único parcial nunca ver duas linhas marcadas ao mesmo tempo).
     */
    @Transactional
    public ClientFileSimulationResponseDTO markSimulationPrincipal(UUID clientId, UUID fileId) {
        ClientFile target = findFileOrThrow(clientId, fileId, FileKind.SIMULATION);
        if (Boolean.TRUE.equals(target.getIsPrincipal())) {
            return toSimulationDTO(target);
        }
        unsetCurrentPrincipal(clientId);
        target.setIsPrincipal(true);
        target.setUpdatedBy(CurrentUser.id());
        return toSimulationDTO(repository.save(target));
    }

    // ── Documento ou simulação (download/delete independem do tipo) ──

    /**
     * Funciona pra documento e simulação - o tipo é resolvido a partir do próprio registro, sem
     * precisar de dois endpoints/métodos idênticos por kind.
     */
    public FileDownload downloadFile(UUID clientId, UUID fileId) {
        return download(findAnyFileOrThrow(clientId, fileId));
    }

    /**
     * Soft delete kind-agnostic. Se o arquivo removido era uma simulação marcada como principal, a
     * mais recente restante é promovida - mesma regra do antigo delete por simulação, só que
     * reaproveitada aqui em vez de duplicada por endpoint.
     */
    @Transactional
    public void deleteFile(UUID clientId, UUID fileId) {
        ClientFile file = findAnyFileOrThrow(clientId, fileId);
        boolean wasPrincipal =
                file.getKind() == FileKind.SIMULATION && Boolean.TRUE.equals(file.getIsPrincipal());
        if (wasPrincipal) {
            file.setIsPrincipal(false);
        }
        softDelete(file);

        if (wasPrincipal) {
            promoteNextPrincipal(clientId);
        }
    }

    // ── Private helpers ──

    private void promoteNextPrincipal(UUID clientId) {
        repository
                .findFirstByClient_IdAndKindAndDeletedAtIsNullOrderByUploadedAtDesc(
                        clientId, FileKind.SIMULATION)
                .ifPresent(
                        remaining -> {
                            remaining.setIsPrincipal(true);
                            remaining.setUpdatedBy(CurrentUser.id());
                            repository.save(remaining);
                        });
    }

    private ClientFile findAnyFileOrThrow(UUID clientId, UUID fileId) {
        findClientOrThrow(clientId);
        return repository
                .findByIdAndClient_IdAndDeletedAtIsNull(fileId, clientId)
                .orElseThrow(() -> NotFoundException.of("Arquivo", fileId));
    }

    private ClientFile newFile(Client client, FileKind kind, MultipartFile file, String folder) {
        StoredFile stored = fileStorageService.store(file, folder);
        ClientFile entity = new ClientFile();
        entity.setClient(client);
        entity.setKind(kind);
        entity.setOriginalFilename(file.getOriginalFilename());
        entity.setStorageKey(stored.storageKey());
        entity.setMimeType(stored.mimeType());
        entity.setFileSizeBytes(stored.sizeBytes());
        entity.setUploadedBy(CurrentUser.id());
        return entity;
    }

    private void unsetCurrentPrincipal(UUID clientId) {
        repository
                .findFirstByClient_IdAndKindAndIsPrincipalTrueAndDeletedAtIsNull(
                        clientId, FileKind.SIMULATION)
                .ifPresent(
                        previous -> {
                            previous.setIsPrincipal(false);
                            previous.setUpdatedBy(CurrentUser.id());
                            repository.saveAndFlush(previous);
                        });
    }

    private FileDownload download(ClientFile file) {
        LoadedFile loaded = fileStorageService.load(file.getStorageKey());
        return new FileDownload(
                loaded.resource(),
                loaded.mimeType(),
                file.getOriginalFilename(),
                file.getFileSizeBytes());
    }

    private void softDelete(ClientFile file) {
        file.setDeletedAt(Instant.now());
        file.setUpdatedBy(CurrentUser.id());
        repository.save(file);
    }

    private Pageable pageable(int pageNumber, int pageSize) {
        return PageRequests.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "uploadedAt"));
    }

    private Client findClientOrThrow(UUID clientId) {
        return ClientLookup.orThrow(clientRepository, clientId);
    }

    private ClientFile findFileOrThrow(UUID clientId, UUID fileId, FileKind kind) {
        findClientOrThrow(clientId);
        return repository
                .findByIdAndClient_IdAndKindAndDeletedAtIsNull(fileId, clientId, kind)
                .orElseThrow(
                        () ->
                                NotFoundException.of(
                                        kind == FileKind.DOCUMENT ? "Documento" : "Simulação",
                                        fileId));
    }

    private void validateBatch(List<MultipartFile> files, int metadataSize) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException(
                    "files", ValidationErrorCode.REQUIRED_FIELD, "Envie ao menos um arquivo.");
        }
        if (metadataSize != files.size()) {
            throw new ValidationException(
                    "metadata",
                    ValidationErrorCode.REQUIRED_FIELD,
                    "A lista de metadados deve ter o mesmo tamanho da lista de arquivos.");
        }
    }

    private DocumentType parseDocumentType(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new ValidationException(
                        "documentType",
                        ValidationErrorCode.REQUIRED_FIELD,
                        "Informe o tipo do documento.");
            }
            return null;
        }
        try {
            return DocumentType.fromLabel(raw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    "documentType",
                    ValidationErrorCode.INVALID_ENUM_VALUE,
                    "Tipo de documento inválido: " + raw);
        }
    }

    private void validateMimeType(MultipartFile file, Set<String> allowed, String message) {
        String contentType = file.getContentType();
        if (contentType == null || !allowed.contains(contentType.toLowerCase())) {
            throw new ValidationException(
                    "files", ValidationErrorCode.INVALID_ENUM_VALUE, message + ": " + contentType);
        }
    }

    private ClientFileDocumentResponseDTO toDocumentDTO(ClientFile entity) {
        ClientFileDocumentResponseDTO dto = new ClientFileDocumentResponseDTO();
        dto.setId(entity.getId());
        dto.setDocumentType(
                entity.getDocumentType() != null ? entity.getDocumentType().getLabel() : null);
        dto.setOriginalFilename(entity.getOriginalFilename());
        dto.setMimeType(entity.getMimeType());
        dto.setFileSizeBytes(entity.getFileSizeBytes());
        dto.setNotes(entity.getNotes());
        dto.setUploadedBy(entity.getUploadedBy());
        dto.setUploadedAt(entity.getUploadedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setDownloadUrl(downloadUrl(entity));
        return dto;
    }

    private ClientFileSimulationResponseDTO toSimulationDTO(ClientFile entity) {
        ClientFileSimulationResponseDTO dto = new ClientFileSimulationResponseDTO();
        dto.setId(entity.getId());
        dto.setOriginalFilename(entity.getOriginalFilename());
        dto.setMimeType(entity.getMimeType());
        dto.setFileSizeBytes(entity.getFileSizeBytes());
        dto.setSimulationDate(entity.getSimulationDate());
        dto.setVersion(entity.getVersion());
        dto.setVinculos(entity.getVinculos());
        dto.setIsPrincipal(entity.getIsPrincipal());
        dto.setNotes(entity.getNotes());
        dto.setUploadedBy(entity.getUploadedBy());
        dto.setUploadedAt(entity.getUploadedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setDownloadUrl(downloadUrl(entity));
        return dto;
    }

    private String downloadUrl(ClientFile entity) {
        return "/api/v1/clients/"
                + entity.getClient().getId()
                + "/files/"
                + entity.getId()
                + "/download";
    }
}
