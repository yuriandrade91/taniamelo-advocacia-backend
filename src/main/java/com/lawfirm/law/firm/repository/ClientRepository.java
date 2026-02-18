package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {
	boolean existsByBenefitNumberIgnoreCase(String benefitNumber);
	boolean existsByCpf(String cpf);
	boolean existsByNitPis(String nitPis);
	boolean existsByCtps(String ctps);
	boolean existsByRg(String rg);
}
