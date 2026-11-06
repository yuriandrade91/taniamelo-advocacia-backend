package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import com.lawfirm.law.firm.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * Compromisso da agenda do escritório (entrevista, reunião, perícia, audiência, prazo). Top-level e
 * tenant-scoped (vive no schema do tenant), com vínculo OPCIONAL a um cliente. Soft delete ({@code
 * deletedAt}) e auditoria automática via {@link AuditLogListener}.
 *
 * <p>Regras: {@code startAt}/{@code endAt} obrigatórios com {@code endAt > startAt} (validado no
 * DTO e por CHECK no banco); toda edição e todo cancelamento exigem justificativa, registrada em
 * {@code appointment_history}.
 */
@Entity
@Table(name = "appointments")
@EntityListeners(AuditLogListener.class)
/*
 * Exclusão lógica filtrada no mapeamento, como em Client: sem isto o filtro deleted_at IS NULL era
 * repetido à mão em cinco lugares, e a sexta query nasceria sem ele. Para alcançar um excluído
 * (listar a lixeira, restaurar), use as consultas NATIVAS do AppointmentRepository - nativa escapa
 * da restrição.
 */
@SQLRestriction("deleted_at IS NULL")
@Getter
public class Appointment implements Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    @Setter
    private String title;

    @Column(name = "type", nullable = false, length = 50)
    @Setter
    private AppointmentType type;

    @Column(name = "start_at", nullable = false)
    @Setter
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    @Setter
    private Instant endAt;

    @Column(name = "modality", nullable = false, length = 20)
    @Setter
    private AppointmentModality modality = AppointmentModality.PRESENCIAL;

    @Column(name = "location")
    @Setter
    private String location;

    @Column(name = "meeting_url", length = 500)
    @Setter
    private String meetingUrl;

    @Column(name = "description")
    @Setter
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private AppointmentStatus status = AppointmentStatus.AGENDADO;

    @Column(name = "cancellation_reason")
    @Setter
    private String cancellationReason;

    /** Vínculo opcional a um cliente cadastrado (FK client_id ON DELETE SET NULL). */
    @Column(name = "client_id")
    @Setter
    private UUID clientId;

    /** Nome livre da pessoa quando NÃO há cliente cadastrado (client_id nulo). */
    @Column(name = "client_name")
    @Setter
    private String clientName;

    /** Usuário que autorizou (confirmou ciência) um agendamento com data no passado. */
    @Column(name = "past_date_authorized_by")
    @Setter
    private UUID pastDateAuthorizedBy;

    @Column(name = "past_date_authorized_at")
    @Setter
    private Instant pastDateAuthorizedAt;

    // ── auditoria ──
    @Column(name = "created_by")
    @Setter
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    @Setter
    private Instant createdAt;

    @Column(name = "updated_by")
    @Setter
    private UUID updatedBy;

    @Column(name = "updated_at")
    @Setter
    private Instant updatedAt;

    @Column(name = "deleted_at")
    @Setter
    private Instant deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }
}
