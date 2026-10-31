package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * A resposta para "esta data é feriado?".
 *
 * <p>{@code verified} existe ao lado de {@code holiday} porque "não é feriado" e "não consegui
 * verificar" são respostas diferentes: com o provedor fora do ar, um simples {@code holiday=false}
 * diria que a data está livre sem ninguém ter olhado.
 */
public record HolidayCheckDTO(
        LocalDate date,
        boolean holiday,
        @Schema(
                        description = "Nome do feriado; nulo quando a data não é feriado",
                        example = "Natal")
                String name,
        @Schema(description = "Falso quando não foi possível consultar o calendário")
                boolean verified,
        @Schema(description = "Texto pronto para mostrar a quem está agendando") String message) {}
