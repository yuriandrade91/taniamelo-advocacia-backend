package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientPaymentRepository extends JpaRepository<ClientPayment, UUID> {

    Page<ClientPayment> findByClient_IdAndDeletedAtIsNull(UUID clientId, Pageable pageable);

    Optional<ClientPayment> findByIdAndClient_IdAndDeletedAtIsNull(UUID id, UUID clientId);
}
