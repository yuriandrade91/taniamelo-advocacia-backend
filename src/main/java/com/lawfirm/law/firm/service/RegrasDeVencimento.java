package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.util.FusoDoEscritorio;
import java.time.LocalDate;

/**
 * Duas regras de data que valem igual para pagamento, despesa e receita.
 *
 * <p>Elas moravam em {@code OfficeExpenseService}, e os outros dois services chamavam o método
 * estático de lá. Funcionava e dizia a coisa errada: sugeria que a regra do atraso é uma
 * particularidade de despesa, que pagamento e receita tomam emprestada. Não é — é a mesma regra do
 * balde {@code overdue} do resumo, e os três a aplicam pelo mesmo motivo.
 *
 * <p>"Atrasado" nunca é persistido. É derivado na leitura, e o dia de referência vem sempre de
 * {@link FusoDoEscritorio}: com o servidor em UTC, entre 21h e 24h de Brasília o dia já virou lá e
 * não aqui, e uma parcela que vence amanhã apareceria como vencida.
 */
public final class RegrasDeVencimento {

    private RegrasDeVencimento() {}

    /** Mesma regra do balde {@code overdue} do resumo — se mudar aqui, muda lá. */
    public static boolean estaAtrasado(PaymentStatus status, LocalDate vencimento) {
        return status == PaymentStatus.PENDENTE
                && vencimento != null
                && vencimento.isBefore(FusoDoEscritorio.hoje());
    }

    /**
     * Data de pagamento coerente com o status.
     *
     * <p>Marcar como pago sem informar a data assume hoje — é o caso comum, alguém confirmando no
     * momento em que o dinheiro entrou ou saiu. Um lançamento com data de pagamento e status
     * Pendente é um estado contraditório que nenhum relatório sabe classificar.
     */
    public static LocalDate dataDePagamento(PaymentStatus status, LocalDate informada) {
        if (status == PaymentStatus.PAGO) {
            return informada != null ? informada : FusoDoEscritorio.hoje();
        }
        return informada;
    }
}
