package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TempoDeContribuicao: anos, meses e dias -> total em meses e frase de exibição")
class TempoDeContribuicaoTest {

    @Nested
    @DisplayName("Total em meses")
    class EmMeses {

        @Test
        @DisplayName("os três nulos devolvem null - 'não informado' não é zero")
        void tresNulosDevolvemNull() {
            // Era exatamente o que o parser de texto errava: "nao informado" virava 0 e ficava
            // indistinguível de quem de fato não tem tempo de contribuição.
            assertNull(TempoDeContribuicao.emMeses(null, null, null));
        }

        @Test
        @DisplayName("zero informado é zero, e não null")
        void zeroInformadoEZero() {
            assertEquals(0, TempoDeContribuicao.emMeses(0, 0, 0));
        }

        @ParameterizedTest(name = "{0} anos, {1} meses, {2} dias -> {3} meses")
        @CsvSource({
            "3, 10, 22, 47",
            "33, 11, 5, 407",
            "1, 0, 0, 12",
            "0, 0, 15, 1",
            "0, 0, 14, 0",
            "0, 0, 29, 1",
        })
        @DisplayName("anos*12 + meses + (dias >= 15 ? 1 : 0)")
        void converteParaMeses(Integer anos, Integer meses, Integer dias, int esperado) {
            assertEquals(esperado, TempoDeContribuicao.emMeses(anos, meses, dias));
        }

        @Test
        @DisplayName("campo ausente entre os três conta como zero")
        void ausenteContaZero() {
            assertEquals(36, TempoDeContribuicao.emMeses(3, null, null));
            assertEquals(2, TempoDeContribuicao.emMeses(null, 2, null));
        }

        @Test
        @DisplayName("o teto de 130 anos mantém o total dentro do int")
        void naoEstoura() {
            // O parser antigo aceitava "300000000 anos" e gravava -694967296. Com a faixa do DTO
            // o pior caso é este, e ele cabe com folga.
            assertEquals(130 * 12 + 11 + 1, TempoDeContribuicao.emMeses(130, 11, 29));
        }
    }

    @Nested
    @DisplayName("Frase de exibição")
    class Formatar {

        @Test
        @DisplayName("os três nulos devolvem null")
        void tresNulosDevolvemNull() {
            assertNull(TempoDeContribuicao.formatar(null, null, null));
        }

        @Test
        @DisplayName("as três parcelas usam vírgula e 'e' antes da última")
        void tresParcelas() {
            assertEquals("33 anos, 11 meses e 5 dias", TempoDeContribuicao.formatar(33, 11, 5));
        }

        @Test
        @DisplayName("parcela zerada não aparece")
        void zeroNaoAparece() {
            assertEquals("10 anos", TempoDeContribuicao.formatar(10, 0, 0));
            assertEquals("10 anos e 3 dias", TempoDeContribuicao.formatar(10, 0, 3));
            assertEquals("2 meses", TempoDeContribuicao.formatar(null, 2, null));
        }

        @Test
        @DisplayName("tudo zerado vira '0 dias' - aí o zero é a informação")
        void tudoZero() {
            assertEquals("0 dias", TempoDeContribuicao.formatar(0, 0, 0));
        }

        @Test
        @DisplayName("singular em cada unidade")
        void singular() {
            assertEquals("1 ano, 1 mês e 1 dia", TempoDeContribuicao.formatar(1, 1, 1));
        }
    }
}
