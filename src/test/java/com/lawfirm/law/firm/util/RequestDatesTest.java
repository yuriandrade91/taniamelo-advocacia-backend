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
    @DisplayName("data simples com startOfDay=true vira 00:00:00 UTC")
    void simpleDateAtStartOfDay() {
        assertEquals(
                Instant.parse("2026-08-20T00:00:00Z"),
                RequestDates.parseInstant("createdFrom", "2026-08-20", true));
    }

    @Test
    @DisplayName("data simples com startOfDay=false vira o último nanossegundo do dia")
    void simpleDateAtEndOfDay() {
        assertEquals(
                Instant.parse("2026-08-20T23:59:59.999999999Z"),
                RequestDates.parseInstant("createdTo", "2026-08-20", false));
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
    @DisplayName("ofYear cobre o ano inteiro em UTC")
    void ofYearCoversWholeYear() {
        RequestDates.Range range = RequestDates.ofYear(2026);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), range.from());
        assertEquals(Instant.parse("2026-12-31T23:59:59.999999999Z"), range.to());
    }

    @Test
    @DisplayName("ofYear funciona em ano bissexto")
    void ofYearHandlesLeapYear() {
        RequestDates.Range range = RequestDates.ofYear(2024);
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), range.from());
        assertTrue(range.to().isAfter(Instant.parse("2024-12-31T00:00:00Z")));
    }
}
