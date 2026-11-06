package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import com.lawfirm.law.firm.audit.Auditable;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Arquivo do cliente - collection única para as abas "Documentos" e "Simulações" da tela do
 * cliente, discriminadas por {@link FileKind}.
 *
 * <p>Campos comuns: metadados do arquivo físico (nunca o binário - o arquivo em si vive no storage
 * via FileStorageService, aqui fica só o storage_key). Campos específicos por tipo são nullable e
 * só preenchidos para o kind correspondente: - DOCUMENT: documentType (um dos 11 tipos) -
 * SIMULATION: simulationDate, version, vinculos, isPrincipal
 *
 * <p>Soft delete (deleted_at): documentos e simulações podem ser evidência em processos - nada é
 * apagado fisicamente pela API.
 */
@Entity
@Table(name = "client_files")
@EntityListeners(AuditLogListener.class)
@Getter
@Setter
public class ClientFile implements Auditable {

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
}
