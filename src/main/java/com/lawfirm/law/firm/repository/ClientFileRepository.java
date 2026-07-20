package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientFile;
import com.lawfirm.law.firm.model.DocumentType;
import com.lawfirm.law.firm.model.FileKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientFileRepository extends JpaRepository<ClientFile, UUID> {

    Page<ClientFile> findByClient_IdAndKindAndDeletedAtIsNull(UUID clientId, FileKind kind, Pageable pageable);

    Page<ClientFile> findByClient_IdAndKindAndDocumentTypeAndDeletedAtIsNull(
            UUID clientId, FileKind kind, DocumentType documentType, Pageable pageable);

    Optional<ClientFile> findByIdAndClient_IdAndKindAndDeletedAtIsNull(UUID id, UUID clientId, FileKind kind);

    Optional<ClientFile> findFirstByClient_IdAndKindAndIsPrincipalTrueAndDeletedAtIsNull(UUID clientId, FileKind kind);

    Optional<ClientFile> findFirstByClient_IdAndKindAndDeletedAtIsNullOrderByUploadedAtDesc(UUID clientId, FileKind kind);
}
