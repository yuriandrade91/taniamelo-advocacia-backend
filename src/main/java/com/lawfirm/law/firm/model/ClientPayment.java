package com.lawfirm.law.firm.model;

import com.lawfirm.law.firm.audit.AuditLogListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Parcela que o <b>cliente tem a receber</b>: atrasados da concessão, benefício mensal, parcela de
 * acordo. Cada linha é uma parcela individual, não o contrato inteiro - algo pago em 6x vira 6
 * linhas com installment_number 1..6.
 *
 * <p><b>Não é honorário.</b> Este dinheiro não passa pelo caixa do escritório; o faturamento é
 * {@link OfficeRevenue}. As duas tabelas têm quase o mesmo formato, e é exatamente por isso que
 * estão separadas: somar uma como se fosse a outra mostra o dinheiro dos clientes como receita do
 * escritório, inflado por um fator que depende só do tamanho dos atrasados. O saldo fica plausível,
 * positivo e falso - já aconteceu uma vez.
 *
 * <p>A migration V7 que criou a tabela ainda a chama de "parcelas de honorários". O texto está
 * errado e fica como está: mexer no arquivo muda o checksum e quebra a validação do Flyway em todo
 * banco que já a aplicou.
 */
@Entity
@Table(name = "client_payments")
@EntityListeners(AuditLogListener.class)
@Getter
@Setter
public class ClientPayment extends FinanceEntry {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installment_total")
    private Integer installmentTotal;
}
