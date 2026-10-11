package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientRequestStageHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRequestStageHistoryRepository
        extends JpaRepository<ClientRequestStageHistory, UUID> {

    Page<ClientRequestStageHistory> findByClient_Id(UUID clientId, Pageable pageable);

    /**
     * Última transição do cliente, para saber quando a etapa atual começou.
     *
     * <p>É o que permite preencher {@code previousStageEnteredAt} na transição seguinte: o começo
     * da etapa que está terminando é o {@code changedAt} da transição que a iniciou.
     */
    Optional<ClientRequestStageHistory> findFirstByClient_IdOrderByChangedAtDesc(UUID clientId);
}
