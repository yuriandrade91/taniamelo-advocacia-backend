package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    List<Tenant> findByStatusOrderByRazaoSocialAsc(String status);

    Optional<Tenant> findBySchemaName(String schemaName);

    Optional<Tenant> findBySlug(String slug);
}
