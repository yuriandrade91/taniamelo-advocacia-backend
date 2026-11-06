package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entrada de caixa do escritório: honorário, parecer avulso, reembolso.
 *
 * <p>É tabela separada de {@link ClientPayment} - e a separação é o ponto. O formato das duas é
 * quase igual (valor, vencimento, pagamento, um cliente ao lado), e é justamente por isso que
 * guardar honorário lá o tornaria indistinguível do dinheiro que o INSS paga ao cliente. A tela de
 * Pagamentos passaria a somar honorário como benefício, e nenhuma consulta conseguiria separar
 * depois.
 *
 * <p>O cliente é opcional: nem toda receita vem de cliente. {@code sourcePayment} aponta a parcela
 * do INSS que originou o honorário quando ele é percentual do êxito - é o que permite conferir "30%
 * dos atrasados" contra o valor de fato, meses depois, sem depender da memória de quem lançou.
 *
 * <p>Esta entidade descreve a receita JÁ APURADA. O contrato que a origina (percentual, parcelas, o
 * que acontece se o INSS pagar menos que o previsto) é modelo à parte, que precisa das regras reais
 * do escritório antes de virar tabela.
 */
@Entity
@Table(name = "office_revenues")
@EntityListeners(AuditLogListener.class)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class OfficeRevenue extends FinanceEntry {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_payment_id")
    private ClientPayment sourcePayment;
}
