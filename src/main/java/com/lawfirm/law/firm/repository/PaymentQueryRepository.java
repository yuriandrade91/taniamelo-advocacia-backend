package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientPayment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Consulta por Specification sobre as parcelas — só isso.
 *
 * <p>É interface separada de propósito. Acrescentar {@code JpaSpecificationExecutor} ao {@link
 * ClientPaymentRepository} faria aparecer lá um {@code delete(Specification)} ao lado do {@code
 * delete(entidade)} que já existe, e toda chamada existente passaria a ser ambígua — o compilador
 * reclamou em código de teste que não tem nada a ver com a visão consolidada.
 *
 * <p>Estende {@link Repository} (a interface vazia), e não {@code JpaRepository}: o que esta visão
 * precisa é ler com filtro dinâmico. Herdar salvar e apagar aqui abriria por acidente um segundo
 * caminho de escrita para parcelas, que pertencem ao cliente e são escritas pela rota dele.
 */
public interface PaymentQueryRepository
        extends Repository<ClientPayment, UUID>, JpaSpecificationExecutor<ClientPayment> {

    /**
     * Traz o cliente junto da página.
     *
     * <p>A linha da lista mostra o nome do cliente, e {@code client} é {@code LAZY}: sem este
     * grafo, uma página de 10 custava 12 consultas — a da página, a de contagem e <b>uma por
     * linha</b> para buscar o nome. Com {@code pageSize=100}, que é o teto de {@code PageRequests},
     * seriam 100 idas ao banco para montar uma tela. Medido, não suposto.
     *
     * <p>Funciona com paginação porque {@code client} é {@code ToOne}: o join não multiplica
     * linhas, então o {@code LIMIT} continua valendo no banco. Com coleção seria o caso em que o
     * Hibernate passa a paginar em memória, e aí o remédio seria pior.
     */
    @Override
    @EntityGraph(attributePaths = "client")
    Page<ClientPayment> findAll(Specification<ClientPayment> spec, Pageable pageable);
}
