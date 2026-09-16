package com.lawfirm.law.firm.util;

import java.time.ZoneId;

/**
 * O fuso em que este escritório vive: {@code America/Sao_Paulo}.
 *
 * <p>Existe porque {@code Instant} é um momento absoluto e não tem dia nem mês. Toda vez que o
 * sistema pergunta "de que mês é este compromisso?", "que dia o filtro cobre?" ou "que idade a
 * pessoa tem hoje?", alguém tem de dizer em que fuso. Estava respondendo em UTC, e UTC está três
 * horas à frente: um compromisso às 21h de 31 de janeiro é 1º de fevereiro em UTC, e sumia do mês
 * de janeiro na agenda. Perto da virada do mês, do ano e do aniversário, a resposta vinha um dia
 * errada.
 *
 * <p>Fixo, e não configurável: o escritório é um só e fica no Brasil. Uma propriedade aqui seria
 * mais uma coisa para configurar errado em produção. Quando houver escritório em outro fuso, o fuso
 * vira coluna do tenant e este arquivo passa a lê-la - a mudança fica num lugar.
 *
 * <p>Não confundir com {@code hibernate.jdbc.time_zone: UTC}: aquilo é como o instante viaja até o
 * banco (e UTC é o certo lá); isto é como o instante vira data para uma pessoa ler.
 */
public final class FusoDoEscritorio {

    /** Identificador IANA, usado também no SQL (AT TIME ZONE). */
    public static final String ID = "America/Sao_Paulo";

    public static final ZoneId ZONA = ZoneId.of(ID);

    private FusoDoEscritorio() {}

    /** Hoje, na data em que o escritório está - não na data em que o servidor está. */
    public static java.time.LocalDate hoje() {
        return java.time.LocalDate.now(ZONA);
    }
}
