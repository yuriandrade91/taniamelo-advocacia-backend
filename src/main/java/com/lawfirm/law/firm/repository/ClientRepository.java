package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByCpf(String cpf);

    boolean existsByNitPis(String nitPis);

    boolean existsByBeneficiaryNumberIgnoreCase(String beneficiaryNumber);
}
