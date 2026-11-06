package com.lawfirm.law.firm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Trilha de alterações de um {@link Appointment}: cada edição/cancelamento gera um registro com a
 * justificativa obrigatória, quem fez e quando (mesmo padrão de {@code ClientSituationHistory}).
 */
@Entity
@Table(name = "appointment_history")
@Getter
public class AppointmentHistory {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    @Setter
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    @Setter
    private AppointmentAction action;

    /** Obrigatória em EDITED/CANCELLED (regra do service); nula em DELETED/RESTORED. */
    @Column(name = "justification")
    @Setter
    private String justification;

    @Column(name = "changed_at", nullable = false)
    @Setter
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by")
    @Setter
    private UUID changedBy;
}
