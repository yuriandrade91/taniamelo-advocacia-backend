package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.model.ClientDisabilityPeriod;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.model.Gender;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * Conversão do tempo de contribuição entre graus de deficiência (LC 142/2013).
 *
 * <h2>Por que é cálculo e não tabela</h2>
 *
 * A tabela de multiplicadores que circula em material de escritório tem 16
 * células por sexo, e todas as 32 saem de uma divisão: <strong>o fator é o
 * tempo exigido no destino dividido pelo tempo exigido na origem</strong>.
 *
 * <p>Mulher, de grave (20 anos) para a regra geral (30): 30 ÷ 20 = 1,50. De
 * moderada (24) para leve (28): 28 ÷ 24 = 1,17. Homem, de 25 para 35: 1,40. As
 * 32 células da tabela estão verificadas em
 * {@code DisabilityTimeConversionTest} — não como números decorados, mas
 * conferindo que a divisão as reproduz.
 *
 * <p>Isso importa por dois motivos. O primeiro é que tabela transcrita à mão
 * erra: a versão que recebemos vinha com a última coluna masculina ("Para 35")
 * faltando, e ninguém notou porque 1,40 / 1,21 / 1,06 / 1,00 não são valores
 * que chamem atenção pela ausência. O segundo é que o sentido da divisão é
 * verificável pelo significado: dez anos trabalhados com deficiência grave são
 * metade de uma carreira de 20 anos, e meia carreira de 30 são 15 — então o
 * fator de 20 para 30 tem de ser maior que 1. Tempo cumprido sob deficiência
 * mais severa vale <em>mais</em> quando convertido para uma base menos severa.
 *
 * <h2>Por que aritmética inteira</h2>
 *
 * O fator é uma razão exata entre dois inteiros pequenos. Guardar 1,1666… como
 * {@code double} e depois multiplicar introduz uma diferença que não se explica
 * para quem contesta o cálculo. Aqui o tempo convertido é
 * {@code dias × exigidoNoDestino ÷ exigidoNaOrigem}, com arredondamento ao dia
 * mais próximo numa única operação. O fator decimal existe apenas para ser
 * exibido ({@link #factor}), nunca como passo intermediário.
 */
public final class DisabilityTimeConversion {

    /** Dias por ano e por mês na contagem de tempo de contribuição. */
    private static final int DAYS_PER_YEAR = 365;

    private static final int DAYS_PER_MONTH = 30;

    private DisabilityTimeConversion() {}

    /**
     * Tempo de contribuição em anos, meses e dias, com o total em dias que o
     * originou.
     *
     * <p>O total em dias fica visível de propósito: é ele que foi somado e
     * convertido, e é por ele que duas contas se conferem. A decomposição em
     * anos/meses/dias é apresentação.
     */
    public record ConvertedTime(long totalDays, int years, int months, int days) {

        public static ConvertedTime ofDays(long totalDays) {
            long remaining = Math.max(totalDays, 0);
            int years = (int) (remaining / DAYS_PER_YEAR);
            remaining %= DAYS_PER_YEAR;
            int months = (int) (remaining / DAYS_PER_MONTH);
            return new ConvertedTime(
                    Math.max(totalDays, 0), years, months, (int) (remaining % DAYS_PER_MONTH));
        }
    }

    /**
     * O multiplicador entre dois graus, arredondado a duas casas — é este o
     * número que a tabela mostra e que a tela exibe ao lado do intervalo.
     *
     * <p>Serve para explicar o resultado, não para produzi-lo: a conversão usa
     * {@link #convertDays}, que não passa por este decimal.
     */
    public static BigDecimal factor(DisabilityGrade from, DisabilityGrade to, Gender gender) {
        return BigDecimal.valueOf(to.requiredYears(gender))
                .divide(BigDecimal.valueOf(from.requiredYears(gender)), 2, RoundingMode.HALF_UP);
    }

    /**
     * Converte uma quantidade de dias de um grau para outro.
     *
     * <p>Uma multiplicação e uma divisão, arredondando ao dia mais próximo.
     * Converter para o mesmo grau devolve o valor intacto, sem passar pelo
     * arredondamento.
     */
    public static long convertDays(
            long days, DisabilityGrade from, DisabilityGrade to, Gender gender) {
        if (from == to) return days;
        int origin = from.requiredYears(gender);
        int target = to.requiredYears(gender);
        return Math.round((double) days * target / origin);
    }

    /** Dias corridos de um intervalo, contando o primeiro e o último dia. */
    public static long daysOf(ClientDisabilityPeriod period, LocalDate today) {
        LocalDate end = period.effectiveEnd(today);
        if (end.isBefore(period.getStartedOn())) return 0;
        return ChronoUnit.DAYS.between(period.getStartedOn(), end) + 1;
    }

    /**
     * Soma os intervalos convertendo cada um pelo seu próprio grau.
     *
     * <p>É o ponto do mecanismo: cada intervalo entra na soma já traduzido para
     * a base de destino, porque um ano sob deficiência grave e um ano sob
     * deficiência leve não valem o mesmo. Somar primeiro e converter depois
     * daria um número diferente — e errado, porque não existe um grau único que
     * descreva a carreira toda.
     */
    public static ConvertedTime totalConverted(
            Collection<ClientDisabilityPeriod> periods,
            DisabilityGrade to,
            Gender gender,
            LocalDate today) {
        long total = 0;
        for (ClientDisabilityPeriod period : periods) {
            total += convertDays(daysOf(period, today), period.getGrade(), to, gender);
        }
        return ConvertedTime.ofDays(total);
    }
}
