package com.lawfirm.law.firm.util;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;

/**
 * Regras de calendário do aniversário, num lugar só e sem dependência de banco (testável).
 *
 * <p>Convenção para quem nasceu em 29/02: em ano não bissexto, o aniversário é comemorado em 28/02
 * (é o que {@link MonthDay#atYear} já faz).
 */
public final class Birthdays {

    private Birthdays() {}

    /** Próxima ocorrência do aniversário a partir de {@code today} (inclusive: hoje conta). */
    public static LocalDate next(LocalDate birthDate, LocalDate today) {
        MonthDay md = MonthDay.from(birthDate);
        LocalDate thisYear = md.atYear(today.getYear());
        return thisYear.isBefore(today) ? md.atYear(today.getYear() + 1) : thisYear;
    }

    public static long daysUntil(LocalDate birthDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, next(birthDate, today));
    }

    /** Idade que a pessoa completa no próximo aniversário. */
    public static int turningAge(LocalDate birthDate, LocalDate today) {
        return next(birthDate, today).getYear() - birthDate.getYear();
    }

    /** Mês*100+dia - o mesmo formato da coluna gerada {@code clients.birth_mmdd}. */
    public static short mmdd(LocalDate date) {
        return (short) (date.getMonthValue() * 100 + date.getDayOfMonth());
    }

    /**
     * Fim da janela em MMDD. Se a janela termina em 28/02 de ano não bissexto, estende para 229:
     * quem nasceu em 29/02 comemora nesse 28/02 e precisa entrar.
     */
    public static short windowEndMmdd(LocalDate windowEnd) {
        boolean feb28NonLeap =
                windowEnd.getMonthValue() == 2
                        && windowEnd.getDayOfMonth() == 28
                        && !windowEnd.isLeapYear();
        return feb28NonLeap ? (short) 229 : mmdd(windowEnd);
    }
}
