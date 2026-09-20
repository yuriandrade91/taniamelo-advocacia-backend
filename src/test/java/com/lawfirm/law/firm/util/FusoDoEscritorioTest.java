package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Este é o teste que CRAVA o fuso.
 *
 * <p>Os outros testes de data usam {@code FusoDoEscritorio.hoje()} em vez de {@code
 * LocalDate.now()}, para não dependerem do fuso da máquina que roda a suíte. Isso resolve a
 * fragilidade, mas sozinho seria circular: trocar o fuso por engano não quebraria nada, porque
 * código e teste passariam a concordar no fuso errado.
 *
 * <p>Aqui a expectativa é escrita à mão. Se alguém mudar {@code America/Sao_Paulo}, quebra neste
 * arquivo - que é onde a decisão está escrita.
 */
@DisplayName("FusoDoEscritorio: o fuso em que este escritório vive")
class FusoDoEscritorioTest {

    @Test
    @DisplayName("é America/Sao_Paulo, e isso é uma decisão, não um acidente")
    void zonaEhSaoPaulo() {
        assertEquals("America/Sao_Paulo", FusoDoEscritorio.ID);
        assertEquals(ZoneId.of("America/Sao_Paulo"), FusoDoEscritorio.ZONA);
    }

    @Test
    @DisplayName("hoje() responde no fuso do escritório, não no da JVM")
    void hojeUsaOFusoDoEscritorio() {
        // A prova que importa, e a razão de este arquivo existir: às 01:30 UTC de 20/09, em São
        // Paulo ainda é dia 19. Foi exatamente assim que o pipeline quebrou - ele roda em UTC, e
        // três testes montavam "hoje" com o relógio da JVM.
        ZonedDateTime instanteDoPipeline =
                ZonedDateTime.parse("2026-09-20T01:30:51Z")
                        .withZoneSameInstant(FusoDoEscritorio.ZONA);
        assertEquals(LocalDate.of(2026, 9, 19), instanteDoPipeline.toLocalDate());

        assertNotNull(FusoDoEscritorio.hoje());
        assertEquals(LocalDate.now(FusoDoEscritorio.ZONA), FusoDoEscritorio.hoje());
    }

    @Test
    @DisplayName(
            "o fuso carrega o horário de verão do passado - por isso ZoneId, e não offset fixo")
    void naoEhOffsetFixo() {
        // O Brasil teve horário de verão até 2019. Fixar -03:00 daria a data errada para
        // qualquer conta sobre período anterior a isso - e este sistema lida com tempo de
        // contribuição, que olha décadas para trás.
        assertEquals(
                java.time.ZoneOffset.ofHours(-2),
                FusoDoEscritorio.ZONA
                        .getRules()
                        .getOffset(ZonedDateTime.parse("2018-01-15T12:00:00Z").toInstant()));
        assertEquals(
                java.time.ZoneOffset.ofHours(-3),
                FusoDoEscritorio.ZONA
                        .getRules()
                        .getOffset(ZonedDateTime.parse("2026-01-15T12:00:00Z").toInstant()));
    }
}
