package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.AppointmentHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, UUID> {

    Page<AppointmentHistory> findByAppointment_Id(UUID appointmentId, Pageable pageable);
}
