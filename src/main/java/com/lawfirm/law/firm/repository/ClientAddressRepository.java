package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientAddress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientAddressRepository extends JpaRepository<ClientAddress, UUID> {

    List<ClientAddress> findByClient_IdOrderByIsPrimaryDescCreatedAtAsc(UUID clientId);

    Page<ClientAddress> findByClient_Id(UUID clientId, Pageable pageable);

    Optional<ClientAddress> findByIdAndClient_Id(UUID id, UUID clientId);

    Optional<ClientAddress> findByClient_IdAndIsPrimaryTrue(UUID clientId);

    long countByClient_Id(UUID clientId);
}
