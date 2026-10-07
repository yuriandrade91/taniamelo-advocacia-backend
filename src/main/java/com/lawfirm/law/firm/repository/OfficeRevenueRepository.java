package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.OfficeRevenue;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficeRevenueRepository
        extends JpaRepository<OfficeRevenue, UUID>, JpaSpecificationExecutor<OfficeRevenue> {

    /**
     * Traz o cliente junto da página — mesmo motivo do {@code PaymentQueryRepository}: a linha
     * mostra de quem veio a entrada e {@code client} é {@code LAZY}. Medido antes do grafo: uma
     * página de 10 fazia 7 consultas extras, uma por cliente distinto.
     *
     * <p>Aqui o join é {@code LEFT} por construção do grafo, e precisa ser: receita sem cliente é
     * caso normal (entrada avulsa do escritório), e um {@code INNER} sumiria com essas linhas.
     */
    @Override
    @EntityGraph(attributePaths = "client")
    Page<OfficeRevenue> findAll(Specification<OfficeRevenue> spec, Pageable pageable);
}
