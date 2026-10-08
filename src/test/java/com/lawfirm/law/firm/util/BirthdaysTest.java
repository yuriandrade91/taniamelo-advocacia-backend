package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BirthdaysTest {

    @Test
    @DisplayName("aniversário ainda neste ano")
    void laterThisYear() {
        LocalDate birth = LocalDate.of(1970, 10, 12);
        LocalDate today = LocalDate.of(2026, 10, 7);

        assertEquals(LocalDate.of(2026, 10, 12), Birthdays.next(birth, today));
        assertEquals(5, Birthdays.daysUntil(birth, today));
        assertEquals(56, Birthdays.turningAge(birth, today));
    }

    @Test
    @DisplayName("aniversário hoje conta como hoje (0 dias), não como ano que vem")
    void today() {
        LocalDate birth = LocalDate.of(1980, 10, 7);
        LocalDate today = LocalDate.of(2026, 10, 7);

        assertEquals(today, Birthdays.next(birth, today));
        assertEquals(0, Birthdays.daysUntil(birth, today));
    }

    @Test
    @DisplayName("aniversário que já passou vai para o ano seguinte (virada de ano)")
    void alreadyPassed() {
        LocalDate birth = LocalDate.of(1990, 1, 5);
        LocalDate today = LocalDate.of(2026, 12, 20);

        assertEquals(LocalDate.of(2027, 1, 5), Birthdays.next(birth, today));
        assertEquals(16, Birthdays.daysUntil(birth, today));
        assertEquals(37, Birthdays.turningAge(birth, today));
    }

    @Test
    @DisplayName("29/02 comemora em 28/02 em ano não bissexto")
    void leapDayInCommonYear() {
        LocalDate birth = LocalDate.of(2000, 2, 29);
        LocalDate today = LocalDate.of(2027, 2, 20);

        assertEquals(LocalDate.of(2027, 2, 28), Birthdays.next(birth, today));
    }

    @Test
    @DisplayName("29/02 cai em 29/02 em ano bissexto")
    void leapDayInLeapYear() {
        LocalDate birth = LocalDate.of(2000, 2, 29);
        LocalDate today = LocalDate.of(2028, 2, 20);

        assertEquals(LocalDate.of(2028, 2, 29), Birthdays.next(birth, today));
    }

    @Test
    @DisplayName("MMDD no mesmo formato da coluna gerada")
    void mmdd() {
        assertEquals(1012, Birthdays.mmdd(LocalDate.of(1970, 10, 12)));
        assertEquals(105, Birthdays.mmdd(LocalDate.of(1990, 1, 5)));
    }

    @Test
    @DisplayName("janela terminando em 28/02 não bissexto inclui quem nasceu em 29/02")
    void windowEndCoversLeapDay() {
        assertEquals(229, Birthdays.windowEndMmdd(LocalDate.of(2027, 2, 28)));
        assertEquals(228, Birthdays.windowEndMmdd(LocalDate.of(2028, 2, 28)));
        assertEquals(1231, Birthdays.windowEndMmdd(LocalDate.of(2026, 12, 31)));
    }
}
