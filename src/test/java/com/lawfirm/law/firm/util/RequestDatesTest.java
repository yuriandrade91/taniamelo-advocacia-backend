package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RequestDates: parsing ISO-8601 dos filtros de período")
class RequestDatesTest {

    @ParameterizedTest(name = "\"{0}\" devolve null")
    @ValueSource(strings = {"", "   "})
    void blankReturnsNull(String raw) {
        assertNull(RequestDates.parseInstant("from", raw, true));
    }

    @Test
    @DisplayName("null devolve null")
    void nullReturnsNull() {
        assertNull(RequestDates.parseInstant("from", null, true));
    }

    @Test
    @DisplayName("timestamp completo é usado como veio")
    void fullTimestampIsParsedAsIs() {
        assertEquals(
                Instant.parse("2026-08-20T14:30:00Z"),
                RequestDates.parseInstant("from", "2026-08-20T14:30:00Z", true));
        assertEquals(
                Instant.parse("2026-08-20T14:30:00Z"),
                RequestDates.parseInstant("to", "  2026-08-20T14:30:00Z  ", false));
    }

    @Test
    @DisplayName("data simples com startOfDay=true vira 00:00 NO FUSO DO ESCRITÓRIO")
    void simpleDateAtStartOfDay() {
        // Meia-noite em São Paulo (UTC-3) são 03:00Z. Em UTC o filtro começava às 21h do dia
        // anterior: "20 de agosto" pegava três horas do dia 19.
        assertEquals(
                Instant.parse("2026-08-20T03:00:00Z"),
                RequestDates.parseInstant("createdFrom", "2026-08-20", true));
    }

    @Test
    @DisplayName("data simples com startOfDay=false vira o último nanossegundo do dia daqui")
    void simpleDateAtEndOfDay() {
        assertEquals(
                Instant.parse("2026-08-21T02:59:59.999999999Z"),
                RequestDates.parseInstant("createdTo", "2026-08-20", false));
    }

    @Test
    @DisplayName("o intervalo de um dia tem exatamente 24 horas")
    void oneDayRangeLastsOneDay() {
        // A garantia que importa não é o offset e sim que as duas pontas usam o MESMO fuso -
        // misturar UTC numa ponta e local na outra daria um intervalo de 21 ou 27 horas.
        Instant inicio = RequestDates.parseInstant("f", "2026-08-20", true);
        Instant fim = RequestDates.parseInstant("t", "2026-08-20", false);
        assertEquals(
                java.time.Duration.ofDays(1).minusNanos(1),
                java.time.Duration.between(inicio, fim));
    }

    @ParameterizedTest(name = "\"{0}\" é rejeitado com 400")
    @ValueSource(strings = {"20/08/2026", "ontem", "2026-13-01", "2026-08-32", "abc"})
    void invalidValueThrowsValidationException(String raw) {
        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> RequestDates.parseInstant("createdFrom", raw, true));
        assertEquals("createdFrom", ex.getField());
        assertEquals(ValidationErrorCode.INVALID_DATE, ex.getValidationErrorCode());
        assertTrue(ex.getMessage().contains(raw));
    }

    @Test
    @DisplayName("ofYear cobre o ano inteiro no fuso do escritório")
    void ofYearCoversWholeYear() {
        // Em UTC, o ano começava às 21h de 31 de dezembro do ano anterior - e o resumo da
        // agenda contava em janeiro um compromisso do fim de dezembro.
        RequestDates.Range range = RequestDates.ofYear(2026);
        assertEquals(Instant.parse("2026-01-01T03:00:00Z"), range.from());
        assertEquals(Instant.parse("2027-01-01T02:59:59.999999999Z"), range.to());
    }

    @Test
    @DisplayName("ofYear funciona em ano bissexto")
    void ofYearHandlesLeapYear() {
        RequestDates.Range range = RequestDates.ofYear(2024);
        assertEquals(Instant.parse("2024-01-01T03:00:00Z"), range.from());
        assertTrue(range.to().isAfter(Instant.parse("2024-12-31T00:00:00Z")));
    }
}
