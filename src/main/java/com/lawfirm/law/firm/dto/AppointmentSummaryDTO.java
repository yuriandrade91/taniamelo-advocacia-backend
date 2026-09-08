package com.lawfirm.law.firm.dto;

/**
 * Contagem de compromissos por mês, para as abas da agenda (ex.: "Agosto 3"). {@code month} é
 * 1-based (1 = janeiro). O rótulo do mês é montado no frontend.
 */
public record AppointmentSummaryDTO(int year, int month, long count) {}
