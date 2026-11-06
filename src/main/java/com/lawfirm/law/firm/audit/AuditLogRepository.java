package com.lawfirm.law.firm.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityNameAndEntityIdOrderByPerformedAtDesc(
            String entityName, UUID entityId, Pageable pageable);
}
