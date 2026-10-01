package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Corpo de POST/PUT de um intervalo de deficiência.
 *
 * <p>{@code endedOn} ausente ou nulo é a resposta afirmativa de "esta deficiência se mantém até a
 * presente data": não é campo que faltou preencher, é a informação de que não houve cessação. Por
 * isso ele não tem {@code @NotNull} e nem default — quem omite está dizendo algo.
 */
public class ClientDisabilityPeriodRequestDTO {

    @Schema(
            description =
                    "Grau da deficiência (Leve, Moderada, Grave). Aceita o rótulo ou o nome da "
                            + "constante. \"Sem deficiência\" é recusado: existe no enum apenas "
                            + "como destino de conversão.",
            example = "Grave")
    @NotBlank
    private String grade;

    @Schema(
            description = "Início da deficiência (yyyy-MM-dd). Não pode ser no futuro.",
            example = "2010-01-01")
    @NotNull
    private LocalDate startedOn;

    @Schema(
            description =
                    "Cessação (yyyy-MM-dd). **Omitir** é a resposta afirmativa de \"esta "
                            + "deficiência se mantém até a presente data\" — não é campo "
                            + "esquecido. Só pode haver um intervalo em aberto por cliente.",
            example = "2019-12-31")
    private LocalDate endedOn;

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(LocalDate startedOn) {
        this.startedOn = startedOn;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public void setEndedOn(LocalDate endedOn) {
        this.endedOn = endedOn;
    }
}
