package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientDisabilityPeriod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientDisabilityPeriodRepository
        extends JpaRepository<ClientDisabilityPeriod, UUID> {

    /**
     * Todos os intervalos do cliente, do mais antigo para o mais recente.
     *
     * <p>Sem paginação de propósito. O cálculo da LC 142/2013 soma a carreira
     * inteira: uma página seria um total errado com cara de certo. São poucas
     * linhas por cliente — o índice único parcial já limita a um intervalo em
     * aberto — e nenhum caminho precisa de meia lista.
     */
    List<ClientDisabilityPeriod> findByClient_IdOrderByStartedOnAsc(UUID clientId);

    Optional<ClientDisabilityPeriod> findByIdAndClient_Id(UUID id, UUID clientId);

    Optional<ClientDisabilityPeriod> findByClient_IdAndEndedOnIsNull(UUID clientId);
}
