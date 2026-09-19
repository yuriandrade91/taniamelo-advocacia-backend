package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository
        extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    Optional<Appointment> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Alcança um compromisso excluído - nativa de propósito: {@code @SQLRestriction} não se aplica
     * a consulta nativa, e sem isso restaurar seria impossível (o registro some das consultas
     * JPQL).
     */
    @Query(value = "SELECT * FROM appointments WHERE id = :id", nativeQuery = true)
    Optional<Appointment> findByIdIncludingDeleted(UUID id);

    /** A lixeira: só os excluídos, mais recentes primeiro. Nativa pelo mesmo motivo. */
    @Query(
            value =
                    "SELECT * FROM appointments WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            countQuery = "SELECT count(*) FROM appointments WHERE deleted_at IS NOT NULL",
            nativeQuery = true)
    Page<Appointment> findDeleted(Pageable pageable);

    /** Compromissos ativos com início no intervalo - base do resumo por mês (abas da agenda). */
    List<Appointment> findByDeletedAtIsNullAndStartAtBetween(Instant from, Instant to);
}
