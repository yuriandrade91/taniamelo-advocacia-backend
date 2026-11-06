package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.model.ClientDisabilityPeriod;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.service.DisabilityTimeConversion.ConvertedTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Conversão de tempo entre graus de deficiência (LC 142/2013)")
class DisabilityTimeConversionTest {

    private static final DisabilityGrade[] ORDER = {
        DisabilityGrade.GRAVE,
        DisabilityGrade.MODERADA,
        DisabilityGrade.LEVE,
        DisabilityGrade.SEM_DEFICIENCIA
    };

    private static ClientDisabilityPeriod period(
            DisabilityGrade grade, LocalDate start, LocalDate end) {
        ClientDisabilityPeriod p = new ClientDisabilityPeriod();
        p.setGrade(grade);
        p.setStartedOn(start);
        p.setEndedOn(end);
        return p;
    }

    @Nested
    @DisplayName("a tabela de multiplicadores")
    class MultiplierTable {

        /**
         * As 16 células da tabela feminina, na ordem grave/moderada/leve/geral — ou seja, "de 20,
         * 24, 28, 30 anos" para "para 20, 24, 28, 30".
         */
        private static final String[][] WOMAN = {
            {"1.00", "1.20", "1.40", "1.50"},
            {"0.83", "1.00", "1.17", "1.25"},
            {"0.71", "0.86", "1.00", "1.07"},
            {"0.67", "0.80", "0.93", "1.00"}
        };

        /**
         * As 16 masculinas — "de 25, 29, 33, 35" para "para 25, 29, 33, 35".
         *
         * <p>A última coluna não vinha no material de origem: estava truncada em três valores por
         * linha. Estes quatro (1,40 / 1,21 / 1,06 / 1,00) saem da mesma divisão que as outras doze,
         * e é por reproduzirem as doze conhecidas que se pode confiar nos quatro que faltavam.
         */
        private static final String[][] MAN = {
            {"1.00", "1.16", "1.32", "1.40"},
            {"0.86", "1.00", "1.14", "1.21"},
            {"0.76", "0.88", "1.00", "1.06"},
            {"0.71", "0.83", "0.94", "1.00"}
        };

        private void check(Gender gender, String[][] expected) {
            for (int from = 0; from < ORDER.length; from++) {
                for (int to = 0; to < ORDER.length; to++) {
                    assertEquals(
                            new BigDecimal(expected[from][to]),
                            DisabilityTimeConversion.factor(ORDER[from], ORDER[to], gender),
                            "de "
                                    + ORDER[from].requiredYears(gender)
                                    + " para "
                                    + ORDER[to].requiredYears(gender)
                                    + " anos ("
                                    + gender.getLabel()
                                    + ")");
                }
            }
        }

        @Test
        @DisplayName("mulher: as 16 células saem de destino ÷ origem")
        void womanTableIsADivision() {
            check(Gender.FEMININO, WOMAN);
        }

        @Test
        @DisplayName("homem: as 16 células saem de destino ÷ origem")
        void manTableIsADivision() {
            check(Gender.MASCULINO, MAN);
        }

        @Test
        @DisplayName("converter para o próprio grau é sempre 1,00")
        void diagonalIsOne() {
            for (DisabilityGrade grade : ORDER) {
                assertEquals(
                        new BigDecimal("1.00"),
                        DisabilityTimeConversion.factor(grade, grade, Gender.FEMININO));
            }
        }

        @Test
        @DisplayName("grau mais severo vale mais na base geral; menos severo vale menos")
        void severityPointsTheRightWay() {
            // Não é redundante com a tabela: é a asserção de SENTIDO. Se a
            // divisão fosse invertida, as 32 células continuariam "batendo"
            // entre si e o cliente perderia tempo de contribuição.
            assertTrue(
                    DisabilityTimeConversion.factor(
                                            DisabilityGrade.GRAVE,
                                            DisabilityGrade.SEM_DEFICIENCIA,
                                            Gender.FEMININO)
                                    .compareTo(BigDecimal.ONE)
                            > 0,
                    "de grave para a regra geral tem de aumentar o tempo");
            assertTrue(
                    DisabilityTimeConversion.factor(
                                            DisabilityGrade.SEM_DEFICIENCIA,
                                            DisabilityGrade.GRAVE,
                                            Gender.FEMININO)
                                    .compareTo(BigDecimal.ONE)
                            < 0,
                    "da regra geral para grave tem de reduzir o tempo");
        }
    }

    @Nested
    @DisplayName("a conversão de dias")
    class DayConversion {

        @Test
        @DisplayName("dez anos em grave valem quinze na regra geral (mulher)")
        void tenYearsGraveBecomeFifteenGeneral() {
            long tenYears = 10L * 365;
            assertEquals(
                    15L * 365,
                    DisabilityTimeConversion.convertDays(
                            tenYears,
                            DisabilityGrade.GRAVE,
                            DisabilityGrade.SEM_DEFICIENCIA,
                            Gender.FEMININO));
        }

        @Test
        @DisplayName("o mesmo grau devolve o valor intacto, sem arredondar")
        void sameGradeIsIdentity() {
            assertEquals(
                    1234L,
                    DisabilityTimeConversion.convertDays(
                            1234L, DisabilityGrade.LEVE, DisabilityGrade.LEVE, Gender.MASCULINO));
        }

        @Test
        @DisplayName("ida e volta não perde mais de um dia de arredondamento")
        void roundTripLosesAtMostOneDay() {
            for (long days : new long[] {1, 7, 365, 3650, 10_001}) {
                long out =
                        DisabilityTimeConversion.convertDays(
                                DisabilityTimeConversion.convertDays(
                                        days,
                                        DisabilityGrade.MODERADA,
                                        DisabilityGrade.SEM_DEFICIENCIA,
                                        Gender.MASCULINO),
                                DisabilityGrade.SEM_DEFICIENCIA,
                                DisabilityGrade.MODERADA,
                                Gender.MASCULINO);
                assertTrue(Math.abs(out - days) <= 1, days + " dias voltaram como " + out);
            }
        }
    }

    @Nested
    @DisplayName("o intervalo")
    class Interval {

        @Test
        @DisplayName("conta o primeiro e o último dia")
        void countsBothEnds() {
            ClientDisabilityPeriod p =
                    period(
                            DisabilityGrade.LEVE,
                            LocalDate.of(2020, 1, 1),
                            LocalDate.of(2020, 1, 31));
            assertEquals(31, DisabilityTimeConversion.daysOf(p, LocalDate.of(2026, 1, 1)));
        }

        @Test
        @DisplayName("sem data de cessação, termina hoje")
        void ongoingEndsToday() {
            ClientDisabilityPeriod p =
                    period(DisabilityGrade.GRAVE, LocalDate.of(2026, 1, 1), null);
            assertTrue(p.isOngoing());
            assertEquals(31, DisabilityTimeConversion.daysOf(p, LocalDate.of(2026, 1, 31)));
        }
    }

    @Nested
    @DisplayName("o total")
    class Total {

        @Test
        @DisplayName("cada intervalo é convertido pelo seu próprio grau antes de somar")
        void eachPeriodConvertsByItsOwnGrade() {
            // Mulher: 20 anos em grave (fator 1,50) + 20 anos em leve (1,07…).
            // Somar primeiro daria 40 anos num grau que não existe.
            var periods =
                    List.of(
                            period(
                                    DisabilityGrade.GRAVE,
                                    LocalDate.of(1990, 1, 1),
                                    LocalDate.of(2009, 12, 27)),
                            period(
                                    DisabilityGrade.LEVE,
                                    LocalDate.of(2010, 1, 1),
                                    LocalDate.of(2029, 12, 26)));

            LocalDate today = LocalDate.of(2030, 1, 1);
            long graveDays = DisabilityTimeConversion.daysOf(periods.get(0), today);
            long leveDays = DisabilityTimeConversion.daysOf(periods.get(1), today);

            ConvertedTime total =
                    DisabilityTimeConversion.totalConverted(
                            periods, DisabilityGrade.SEM_DEFICIENCIA, Gender.FEMININO, today);

            // Cada um pelo seu fator: 30/20 no grave, 30/28 no leve.
            assertEquals(
                    DisabilityTimeConversion.convertDays(
                                    graveDays,
                                    DisabilityGrade.GRAVE,
                                    DisabilityGrade.SEM_DEFICIENCIA,
                                    Gender.FEMININO)
                            + DisabilityTimeConversion.convertDays(
                                    leveDays,
                                    DisabilityGrade.LEVE,
                                    DisabilityGrade.SEM_DEFICIENCIA,
                                    Gender.FEMININO),
                    total.totalDays());

            // E a afirmação que dá sentido ao teste: somar antes e converter
            // depois dá OUTRO número. Com os dois intervalos no mesmo tamanho,
            // qualquer grau único que se escolhesse para a soma erraria — o
            // grave puxaria para cima, o leve para baixo.
            long somaCrua = graveDays + leveDays;
            assertTrue(
                    DisabilityTimeConversion.convertDays(
                                    somaCrua,
                                    DisabilityGrade.GRAVE,
                                    DisabilityGrade.SEM_DEFICIENCIA,
                                    Gender.FEMININO)
                            > total.totalDays(),
                    "tratar os 40 anos como graves infla o tempo");
            assertTrue(
                    DisabilityTimeConversion.convertDays(
                                    somaCrua,
                                    DisabilityGrade.LEVE,
                                    DisabilityGrade.SEM_DEFICIENCIA,
                                    Gender.FEMININO)
                            < total.totalDays(),
                    "tratar os 40 anos como leves encurta o tempo");
        }

        @Test
        @DisplayName("sem intervalo nenhum, o total é zero")
        void emptyIsZero() {
            ConvertedTime total =
                    DisabilityTimeConversion.totalConverted(
                            List.of(),
                            DisabilityGrade.SEM_DEFICIENCIA,
                            Gender.MASCULINO,
                            LocalDate.of(2026, 1, 1));
            assertEquals(0, total.totalDays());
            assertEquals(0, total.years());
        }

        @Test
        @DisplayName("anos, meses e dias saem do total em dias")
        void breakdownComesFromTotalDays() {
            ConvertedTime t = ConvertedTime.ofDays(365 + 30 + 30 + 5);
            assertEquals(1, t.years());
            assertEquals(2, t.months());
            assertEquals(5, t.days());
        }
    }

    @Nested
    @DisplayName("sexo sem base na lei")
    class NoLegalBasis {

        @Test
        @DisplayName("não-binário e outro não têm coluna na LC 142/2013 — recusa em vez de supor")
        void refusesInsteadOfGuessing() {
            for (Gender gender : List.of(Gender.NAO_BINARIO, Gender.OUTRO)) {
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                DisabilityTimeConversion.factor(
                                        DisabilityGrade.GRAVE,
                                        DisabilityGrade.SEM_DEFICIENCIA,
                                        gender),
                        gender + " deveria recusar");
                assertTrue(!DisabilityGrade.hasBasisFor(gender));
            }
            assertTrue(DisabilityGrade.hasBasisFor(Gender.FEMININO));
            assertTrue(DisabilityGrade.hasBasisFor(Gender.MASCULINO));
        }
    }
}
