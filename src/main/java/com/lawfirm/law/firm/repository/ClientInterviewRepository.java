package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientInterview;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientInterviewRepository extends JpaRepository<ClientInterview, UUID> {

    Page<ClientInterview> findByClient_IdAndDeletedAtIsNull(UUID clientId, Pageable pageable);

    Optional<ClientInterview> findByIdAndClient_IdAndDeletedAtIsNull(UUID id, UUID clientId);
}
