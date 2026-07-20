package com.lawfirm.law.firm.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Arquivo do cliente - collection única para as abas "Documentos" e
 * "Simulações" da tela do cliente, discriminadas por {@link FileKind}.
 *
 * Campos comuns: metadados do arquivo físico (nunca o binário - o arquivo em
 * si vive no storage via FileStorageService, aqui fica só o storage_key).
 * Campos específicos por tipo são nullable e só preenchidos para o kind
 * correspondente:
 *  - DOCUMENT: documentType (um dos 11 tipos)
 *  - SIMULATION: simulationDate, version, vinculos, isPrincipal
 *
 * Soft delete (deleted_at): documentos e simulações podem ser evidência em
 * processos - nada é apagado fisicamente pela API.
 */
@Entity
@Table(name = "client_files")
public class ClientFile {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private FileKind kind;

    // ── Comum a todos os arquivos ──

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Específico de DOCUMENT ──

    @Column(name = "document_type", length = 60)
    private DocumentType documentType;

    // ── Específico de SIMULATION ──

    @Column(name = "simulation_date")
    private LocalDate simulationDate;

    @Column(name = "version", length = 30)
    private String version;

    @Column(name = "vinculos")
    private Integer vinculos;

    @Column(name = "is_principal", nullable = false)
    private Boolean isPrincipal = false;

    // ── Auditoria ──

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.uploadedAt == null) this.uploadedAt = Instant.now();
        if (this.isPrincipal == null) this.isPrincipal = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public FileKind getKind() { return kind; }
    public void setKind(FileKind kind) { this.kind = kind; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public LocalDate getSimulationDate() { return simulationDate; }
    public void setSimulationDate(LocalDate simulationDate) { this.simulationDate = simulationDate; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Integer getVinculos() { return vinculos; }
    public void setVinculos(Integer vinculos) { this.vinculos = vinculos; }

    public Boolean getIsPrincipal() { return isPrincipal; }
    public void setIsPrincipal(Boolean isPrincipal) { this.isPrincipal = isPrincipal; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
