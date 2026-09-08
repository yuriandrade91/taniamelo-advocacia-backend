package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppointmentRepository
        extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByIdAndDeletedAtIsNull(UUID id);

    /** Compromissos ativos com início no intervalo - base do resumo por mês (abas da agenda). */
    List<Appointment> findByDeletedAtIsNullAndStartAtBetween(Instant from, Instant to);
}
