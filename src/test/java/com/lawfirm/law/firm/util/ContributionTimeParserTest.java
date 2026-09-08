package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ContributionTimeParser: texto livre de tempo de contribuição -> meses")
class ContributionTimeParserTest {

    @Test
    @DisplayName("null devolve null")
    void nullReturnsNull() {
        assertNull(ContributionTimeParser.toMonths(null));
    }

    @ParameterizedTest(name = "\"{0}\" (em branco) devolve null")
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void blankReturnsNull(String raw) {
        assertNull(ContributionTimeParser.toMonths(raw));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1} meses")
    @CsvSource({
        "'3 anos, 10 meses, 22 dias', 47",
        "'3 anos, 10 meses, 14 dias', 46",
        "'3 anos, 10 meses, 15 dias', 47",
        "'1 ano', 12",
        "'1 ano, 1 mes', 13",
        "'0 anos, 0 meses, 0 dias', 0",
        "'20 dias', 1",
        "'14 dias', 0",
        "'11 meses', 11",
        "'35 ANOS', 420",
        "'2   anos    5   meses', 29",
        "'2anos5meses', 29",
        "'nenhum número aqui', 0"
    })
    void parsesYearsMonthsAndDays(String raw, int expected) {
        assertEquals(expected, ContributionTimeParser.toMonths(raw));
    }

    @Test
    @DisplayName("\"mês\" acentuado NÃO é reconhecido pelo regex (comportamento atual)")
    void accentedMonthIsNotMatched() {
        assertEquals(12, ContributionTimeParser.toMonths("1 ano, 1 mês"));
        assertEquals(13, ContributionTimeParser.toMonths("1 ano, 1 mes"));
    }

    @Test
    @DisplayName("dias >= 15 arredondam para mais um mês, < 15 são descartados")
    void roundsDaysAtFifteen() {
        assertEquals(1, ContributionTimeParser.toMonths("15 dias"));
        assertEquals(0, ContributionTimeParser.toMonths("1 dia"));
    }

    @Test
    @DisplayName("espaços em volta são ignorados")
    void trimsInput() {
        assertEquals(12, ContributionTimeParser.toMonths("   1 ano   "));
    }

    @Test
    @DisplayName("número maior que Integer.MAX_VALUE é ignorado sem quebrar")
    void overflowingNumberIsIgnored() {
        assertEquals(0, ContributionTimeParser.toMonths("99999999999999 anos"));
    }
}
