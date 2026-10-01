package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.DisabilityGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um intervalo de deficiência como a tela o lê.
 *
 * <p>{@code ongoing} existe ao lado de {@code endedOn} nulo porque a tela precisa distinguir, sem
 * interpretar ausência, entre "mantém até a presente data" e "não informado" — e a opção que ela
 * oferece tem essas palavras.
 *
 * <p>{@code days} vem calculado do servidor, com o fim do intervalo aberto resolvido no fuso do
 * escritório. Deixar a tela contar os dias faria o número depender do relógio e do fuso do
 * navegador: às 21h de 31 de janeiro, um navegador em UTC já estaria em fevereiro e devolveria um
 * dia a mais.
 */
public class ClientDisabilityPeriodResponseDTO {

    private UUID id;

    @Schema(description = "Grau, como rótulo", example = "Grave")
    private DisabilityGrade grade;

    private LocalDate startedOn;

    @Schema(description = "Nulo quando a deficiência se mantém até a presente data")
    private LocalDate endedOn;

    @Schema(description = "Verdadeiro quando não houve cessação — equivale a endedOn nulo")
    private boolean ongoing;

    @Schema(
            description =
                    "Dias corridos, contando o primeiro e o último dia. Calculado no servidor: "
                            + "o fim de um intervalo em aberto é hoje no fuso do escritório.",
            example = "7305")
    private long days;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DisabilityGrade getGrade() {
        return grade;
    }

    public void setGrade(DisabilityGrade grade) {
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

    public boolean isOngoing() {
        return ongoing;
    }

    public void setOngoing(boolean ongoing) {
        this.ongoing = ongoing;
    }

    public long getDays() {
        return days;
    }

    public void setDays(long days) {
        this.days = days;
    }
}
