package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.ClientPayment;
import java.util.UUID;
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
        extends Repository<ClientPayment, UUID>, JpaSpecificationExecutor<ClientPayment> {}
