package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Corpo de POST/PUT de um intervalo de deficiência.
 *
 * <p>{@code endedOn} ausente ou nulo é a resposta afirmativa de "esta
 * deficiência se mantém até a presente data": não é campo que faltou
 * preencher, é a informação de que não houve cessação. Por isso ele não tem
 * {@code @NotNull} e nem default — quem omite está dizendo algo.
 */
public class ClientDisabilityPeriodRequestDTO {

    /** "Leve", "Moderada" ou "Grave" — ou o nome do enum. */
    @NotBlank
    private String grade;

    @NotNull private LocalDate startedOn;

    /** Nulo = sem data de cessação (deficiência em curso). */
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
