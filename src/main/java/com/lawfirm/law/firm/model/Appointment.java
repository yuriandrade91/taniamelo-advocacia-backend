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
public class Appointment implements Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "type", nullable = false, length = 50)
    private AppointmentType type;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "modality", nullable = false, length = 20)
    private AppointmentModality modality = AppointmentModality.PRESENCIAL;

    @Column(name = "location")
    private String location;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.AGENDADO;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    /** Vínculo opcional a um cliente cadastrado (FK client_id ON DELETE SET NULL). */
    @Column(name = "client_id")
    private UUID clientId;

    /** Nome livre da pessoa quando NÃO há cliente cadastrado (client_id nulo). */
    @Column(name = "client_name")
    private String clientName;

    /** Usuário que autorizou (confirmou ciência) um agendamento com data no passado. */
    @Column(name = "past_date_authorized_by")
    private UUID pastDateAuthorizedBy;

    @Column(name = "past_date_authorized_at")
    private Instant pastDateAuthorizedAt;

    // ── auditoria ──
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
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AppointmentType getType() {
        return type;
    }

    public void setType(AppointmentType type) {
        this.type = type;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public AppointmentModality getModality() {
        return modality;
    }

    public void setModality(AppointmentModality modality) {
        this.modality = modality;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public void setMeetingUrl(String meetingUrl) {
        this.meetingUrl = meetingUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public UUID getPastDateAuthorizedBy() {
        return pastDateAuthorizedBy;
    }

    public void setPastDateAuthorizedBy(UUID pastDateAuthorizedBy) {
        this.pastDateAuthorizedBy = pastDateAuthorizedBy;
    }

    public Instant getPastDateAuthorizedAt() {
        return pastDateAuthorizedAt;
    }

    public void setPastDateAuthorizedAt(Instant pastDateAuthorizedAt) {
        this.pastDateAuthorizedAt = pastDateAuthorizedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
