package com.lawfirm.law.firm.util;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Parsing de datas de parâmetros de requisição, compartilhado entre os controllers (evita duplicar
 * a mesma lógica em cada listagem). Tudo em UTC, coerente com o {@code time_zone=UTC} da camada
 * JDBC.
 */
public final class RequestDates {

    private RequestDates() {}

    /** Intervalo [from, to] fechado, para filtros de período. */
    public record Range(Instant from, Instant to) {}

    /**
     * Aceita ISO-8601: data simples ({@code yyyy-MM-dd}) ou timestamp completo. Valor inválido gera
     * 400 explícito (nunca é ignorado silenciosamente). {@code startOfDay} decide se uma data
     * simples vira o início (00:00:00) ou o fim (23:59:59.999999999) do dia.
     */
    public static Instant parseInstant(String field, String value, boolean startOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // não é timestamp completo; tenta como data simples abaixo
        }
        try {
            LocalDate date = LocalDate.parse(trimmed);
            return startOfDay
                    ? date.atStartOfDay(ZoneOffset.UTC).toInstant()
                    : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    field,
                    ValidationErrorCode.INVALID_DATE,
                    "Data inválida (use ISO-8601, ex.: 2026-08-20 ou 2026-08-20T14:30:00Z): "
                            + value);
        }
    }

    /** Intervalo cobrindo o ano inteiro (UTC). */
    public static Range ofYear(int year) {
        return new Range(
                LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC));
    }
}
