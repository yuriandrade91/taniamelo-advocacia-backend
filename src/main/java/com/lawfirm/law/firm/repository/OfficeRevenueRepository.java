package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.OfficeRevenue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficeRevenueRepository
        extends JpaRepository<OfficeRevenue, UUID>, JpaSpecificationExecutor<OfficeRevenue> {}
