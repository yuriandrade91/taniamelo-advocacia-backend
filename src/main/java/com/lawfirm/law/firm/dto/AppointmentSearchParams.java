package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Filtros e paginação da listagem da agenda, agrupados num único objeto (bind via
 * {@code @ParameterObject}) para não inflar a assinatura do controller/service. Todos os campos são
 * opcionais; {@code from}/{@code to} são ISO-8601 (data ou timestamp) resolvidos no service.
 *
 * <p>{@code year}, {@code month}, {@code type} e {@code status} aceitam múltiplos valores (repita o
 * parâmetro na query string, ex.: {@code ?year=2025&year=2026&type=Reunião&type=Audiência}) - um
 * único valor continua funcionando normalmente, pois vira uma lista de um elemento só.
 */
@Getter
@Setter
public class AppointmentSearchParams {

    @Schema(description = "Número da página (1-based)", defaultValue = "1")
    private int pageNumber = 1;

    @Schema(
            description = "Tamanho da página (máximo 100; valores acima são cortados no teto)",
            defaultValue = "10")
    private int pageSize = 10;

    @Schema(
            description =
                    "Ano(s) de referência (aba do mês ou o(s) ano(s) todo(s)) - aceita múltiplos valores")
    private List<Integer> year;

    @Schema(description = "Mês(es) 1-12 - aceita múltiplos valores")
    private List<Integer> month;

    @Schema(description = "Tipo(s), nome ou label (ex.: Entrevista) - aceita múltiplos valores")
    private List<String> type;

    @Schema(description = "Situação(ões), nome ou label (ex.: Agendado) - aceita múltiplos valores")
    private List<String> status;

    @Schema(description = "Filtra por cliente vinculado")
    private UUID clientId;

    @Schema(description = "Busca por título")
    private String searchTerm;

    @Schema(description = "Início a partir de (ISO-8601)")
    private String from;

    @Schema(description = "Início até (ISO-8601)")
    private String to;
}
