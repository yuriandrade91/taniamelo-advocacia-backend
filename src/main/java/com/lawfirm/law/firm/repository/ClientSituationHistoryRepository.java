package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientSituationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientSituationHistoryRepository extends JpaRepository<ClientSituationHistory, UUID> {

    Page<ClientSituationHistory> findByClient_Id(UUID clientId, Pageable pageable);
}
