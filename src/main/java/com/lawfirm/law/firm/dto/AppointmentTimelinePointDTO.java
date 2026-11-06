package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Um mês da série que alimenta o gráfico do relatório de agenda.
 *
 * <p>Meses sem compromisso nenhum vêm no array, zerados — mesma razão do {@link
 * FinanceTimelinePointDTO}: omiti-los faria o gráfico colar dois meses distantes lado a lado e
 * sumir justamente com o mês vazio, que num relatório de agenda é a informação (ninguém agendou
 * nada em janeiro).
 *
 * <p>{@code totalCount} é a soma dos três status, cancelados inclusive. É redundante de propósito:
 * um total que não fecha com as barras visíveis do gráfico é mais confuso do que útil, e aqui ele
 * fecha por construção — quem monta este ponto soma, não conta de novo.
 *
 * <p>Diferente de {@link AppointmentSummaryDTO}, que conta só os PENDENTES para as abas da agenda,
 * aqui entram os três status: o relatório quer o histórico do mês, não o que ainda está por fazer.
 */
public record AppointmentTimelinePointDTO(
        @Schema(description = "Mês no formato yyyy-MM", example = "2026-05") String month,
        @Schema(description = "scheduledCount + completedCount + cancelledCount") long totalCount,
        @Schema(description = "Status Agendado") long scheduledCount,
        @Schema(description = "Status Concluído") long completedCount,
        @Schema(description = "Status Cancelado") long cancelledCount) {

    /** Garante que o total feche com as partes: ele é derivado, nunca informado. */
    public static AppointmentTimelinePointDTO of(
            String month, long scheduled, long completed, long cancelled) {
        return new AppointmentTimelinePointDTO(
                month, scheduled + completed + cancelled, scheduled, completed, cancelled);
    }
}
