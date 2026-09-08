package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Client;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ClientRepository
        extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByCpf(String cpf);

    boolean existsByNitPis(String nitPis);

    boolean existsByBeneficiaryNumberIgnoreCase(String beneficiaryNumber);

    /**
     * Busca ignorando a exclusão lógica - o único caminho até um cliente excluído.
     *
     * <p>Precisa ser nativa: o {@code @SQLRestriction} de {@link Client} é aplicado pelo Hibernate
     * a qualquer consulta JPA da entidade, inclusive JPQL, e filtraria justamente a linha que se
     * quer encontrar. Existe para restaurar; nenhum fluxo de leitura normal deve usá-la.
     */
    @Query(value = "SELECT * FROM clients WHERE id = :id", nativeQuery = true)
    Optional<Client> findByIdIncludingDeleted(UUID id);
}
