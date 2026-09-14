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

    /*
     * Comparação exata, e não IgnoreCase: o valor chega normalizado (só dígitos), e
     * era justamente o descompasso entre a checagem IgnoreCase da aplicação e o
     * índice sensível a caixa do banco que fazia os dois discordarem sobre o que é
     * duplicado.
     */
    boolean existsByBeneficiaryNumber(String beneficiaryNumber);

    /*
     * Variantes "e não seja eu": a validação de unicidade passou a valer também na
     * EDIÇÃO, e lá o próprio registro é um resultado legítimo - sem excluir o id
     * atual, salvar um cliente sem mexer no CPF acusaria duplicidade contra ele
     * mesmo.
     *
     * Os valores chegam normalizados (DocumentoIdentidade), então a comparação é
     * exata de propósito: é o mesmo texto que o índice único do banco compara.
     */

    boolean existsByCpfAndIdNot(String cpf, UUID id);

    boolean existsByRg(String rg);

    boolean existsByRgAndIdNot(String rg, UUID id);

    boolean existsByCtps(String ctps);

    boolean existsByCtpsAndIdNot(String ctps, UUID id);

    boolean existsByNitPisAndIdNot(String nitPis, UUID id);

    boolean existsByBeneficiaryNumberAndIdNot(String beneficiaryNumber, UUID id);

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
