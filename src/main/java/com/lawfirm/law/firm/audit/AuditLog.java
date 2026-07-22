package com.lawfirm.law.firm.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Linha imutável da trilha de auditoria: quem fez o quê, em qual registro, e quando. Gravada só por
 * {@link AuditLogListener} - nunca editada nem removida pela aplicação.
 *
 * <p>Deliberadamente NÃO implementa {@link Auditable} nem carrega {@code @EntityListeners}: auditar
 * a própria auditoria causaria recursão.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "detail", length = 255)
    private String detail;

    public AuditLog() {}

    public AuditLog(
            String entityName, UUID entityId, AuditAction action, UUID performedBy, String detail) {
        this.entityName = entityName;
        this.entityId = entityId;
        this.action = action;
        this.performedBy = performedBy;
        this.performedAt = Instant.now();
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public String getEntityName() {
        return entityName;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public String getDetail() {
        return detail;
    }
}
