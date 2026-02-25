package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientSituationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientSituationHistoryRepository extends JpaRepository<ClientSituationHistory, UUID> {
    // query by nested client id property, newest first
    List<ClientSituationHistory> findByClient_IdOrderByChangedAtDesc(UUID clientId);

    // pageable query for history (caller defines sorting); used by service for paged responses
    org.springframework.data.domain.Page<ClientSituationHistory> findByClient_Id(UUID clientId, org.springframework.data.domain.Pageable pageable);
}
