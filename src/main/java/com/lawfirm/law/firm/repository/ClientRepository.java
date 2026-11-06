package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * A lixeira: só os excluídos, mais recentes primeiro. Nativa pelo mesmo motivo da de cima.
     *
     * <p>Sem isto, {@code PATCH /{id}/restore} só era utilizável por quem tivesse anotado o UUID
     * antes de excluir - ou seja, desfazer existia na API e não era alcançável.
     */
    @Query(
            value =
                    "SELECT * FROM clients WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, id",
            countQuery = "SELECT count(*) FROM clients WHERE deleted_at IS NOT NULL",
            nativeQuery = true)
    Page<Client> findDeleted(Pageable pageable);

    /**
     * Aniversariantes numa janela de dias, já na ordem do próximo aniversário.
     *
     * <p>A janela é um range sobre {@code birth_mmdd} (coluna gerada e indexada, V22): de {@code
     * fromMmdd} (hoje) a {@code toMmdd} (hoje + N dias). Quando a janela atravessa o ano ({@code
     * wraps}, ex.: 20/12 a 10/01), vira "a partir de hoje OU até o fim da janela". {@code allYear}
     * desliga o filtro (janela de um ano inteiro). A ordem põe primeiro quem ainda faz aniversário
     * este ano, depois os do começo do próximo. Os excluídos saem pelo {@code @SQLRestriction} da
     * entidade.
     */
    @Query(
            """
            SELECT new com.lawfirm.law.firm.repository.BirthdayRow(
                c.id, c.fullName, c.birthDate, c.mobilePhone, c.isWhatsapp)
            FROM Client c
            WHERE :allYear = true
               OR (:wraps = false AND c.birthMmdd BETWEEN :fromMmdd AND :toMmdd)
               OR (:wraps = true AND (c.birthMmdd >= :fromMmdd OR c.birthMmdd <= :toMmdd))
            ORDER BY CASE WHEN c.birthMmdd >= :fromMmdd THEN 0 ELSE 1 END,
                     c.birthMmdd,
                     c.fullName
            """)
    List<BirthdayRow> findBirthdaysInWindow(
            short fromMmdd, short toMmdd, boolean wraps, boolean allYear, Pageable pageable);
}
