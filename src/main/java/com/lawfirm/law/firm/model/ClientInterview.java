package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import com.lawfirm.law.firm.audit.Auditable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entrevista/atendimento registrado com o cliente. Campos: data (occurredAt), duração em minutos e
 * conteúdo vindo de um componente rich text (HTML/JSON do editor, armazenado como TEXT sem
 * transformação).
 *
 * <p>Soft delete: o conteúdo de uma entrevista pode ser evidência relevante depois (ex.:
 * divergência sobre o que foi combinado com o cliente).
 */
@Entity
@Table(name = "client_interviews")
@EntityListeners(AuditLogListener.class)
@Getter
@Setter
public class ClientInterview implements Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Conteúdo do componente rich text, persistido como enviado. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.occurredAt == null) this.occurredAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
