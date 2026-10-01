package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.DisabilityGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O tempo de deficiência convertido para uma base, com a conta aberta.
 *
 * <p>A conta vem aberta de propósito. Este número vai para dentro de um requerimento, e quem o
 * defende precisa poder mostrar de onde saiu: cada intervalo traz o próprio fator e o próprio
 * resultado, e o total é a soma do que está visível. Devolver só o total obrigaria o escritório a
 * refazer o cálculo à mão para conferir — e aí a tela seria um palpite a mais, não uma ferramenta.
 */
public class DisabilityConversionDTO {

    /** Base para a qual se converteu (normalmente "Sem deficiência"). */
    private DisabilityGrade convertedTo;

    /** Anos exigidos na base de destino, para o sexo deste cliente. */
    private int targetYears;

    private List<Line> lines;

    private long totalDays;
    private int totalYears;
    private int totalMonths;
    private int totalRemainingDays;

    /** Frase pronta: "33 anos, 11 meses e 5 dias". */
    private String totalLabel;

    /** Uma linha da conta: um intervalo, seu fator e seu resultado. */
    public static class Line {
        private DisabilityGrade grade;
        private int gradeYears;
        private long days;
        private BigDecimal factor;
        private long convertedDays;

        @Schema(description = "Início do intervalo", example = "2000-01-01")
        private LocalDate startedOn;

        /**
         * O fim que ENTROU na conta.
         *
         * <p>Num intervalo em aberto é o dia de hoje no fuso do escritório, não {@code null}: a
         * linha existe para mostrar de onde saiu o número de dias, e "até hoje" sem dizer que dia é
         * hoje deixa a conta sem conferência — ela seria irreproduzível amanhã.
         */
        @Schema(
                description = "Fim considerado; num intervalo em aberto, hoje",
                example = "2005-12-31")
        private LocalDate endedOn;

        @Schema(description = "Se o intervalo não tem data de cessação")
        private boolean ongoing;

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

        public DisabilityGrade getGrade() {
            return grade;
        }

        public void setGrade(DisabilityGrade grade) {
            this.grade = grade;
        }

        public int getGradeYears() {
            return gradeYears;
        }

        public void setGradeYears(int gradeYears) {
            this.gradeYears = gradeYears;
        }

        public long getDays() {
            return days;
        }

        public void setDays(long days) {
            this.days = days;
        }

        public BigDecimal getFactor() {
            return factor;
        }

        public void setFactor(BigDecimal factor) {
            this.factor = factor;
        }

        public long getConvertedDays() {
            return convertedDays;
        }

        public void setConvertedDays(long convertedDays) {
            this.convertedDays = convertedDays;
        }
    }

    public DisabilityGrade getConvertedTo() {
        return convertedTo;
    }

    public void setConvertedTo(DisabilityGrade convertedTo) {
        this.convertedTo = convertedTo;
    }

    public int getTargetYears() {
        return targetYears;
    }

    public void setTargetYears(int targetYears) {
        this.targetYears = targetYears;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public long getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(long totalDays) {
        this.totalDays = totalDays;
    }

    public int getTotalYears() {
        return totalYears;
    }

    public void setTotalYears(int totalYears) {
        this.totalYears = totalYears;
    }

    public int getTotalMonths() {
        return totalMonths;
    }

    public void setTotalMonths(int totalMonths) {
        this.totalMonths = totalMonths;
    }

    public int getTotalRemainingDays() {
        return totalRemainingDays;
    }

    public void setTotalRemainingDays(int totalRemainingDays) {
        this.totalRemainingDays = totalRemainingDays;
    }

    public String getTotalLabel() {
        return totalLabel;
    }

    public void setTotalLabel(String totalLabel) {
        this.totalLabel = totalLabel;
    }
}
