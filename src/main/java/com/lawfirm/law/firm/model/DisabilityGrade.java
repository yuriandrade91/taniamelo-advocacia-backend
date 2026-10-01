package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;
import java.util.List;

/**
 * Grau da deficiência e o tempo de contribuição que ele exige (LC 142/2013).
 *
 * Os números não são parâmetro de negócio: estão no art. 3º da LC 142/2013 —
 * 20/24/28 anos para a mulher e 25/29/33 para o homem, conforme a deficiência
 * seja grave, moderada ou leve. {@link #SEM_DEFICIENCIA} carrega 30 e 35, que
 * é a regra geral de tempo de contribuição, e existe aqui por um motivo só:
 * ser origem ou destino de uma conversão.
 *
 * <p>Por isso ele <strong>não</strong> é um grau que se possa gravar num
 * intervalo — "período de deficiência sem deficiência" é contradição, e
 * {@link #asDisability()} é o filtro que a API e a tela usam para oferecer
 * apenas os três reais.
 *
 * <p>O par de números por grau é o que torna a conversão um cálculo em vez de
 * uma tabela decorada: o fator é o tempo exigido no destino dividido pelo
 * tempo exigido na origem. Ver {@code DisabilityTimeConversion}.
 */
public enum DisabilityGrade {
    GRAVE("Grave", 20, 25),
    MODERADA("Moderada", 24, 29),
    LEVE("Leve", 28, 33),
    SEM_DEFICIENCIA("Sem deficiência", 30, 35);

    private final String label;
    private final int womanYears;
    private final int manYears;

    DisabilityGrade(String label, int womanYears, int manYears) {
        this.label = label;
        this.womanYears = womanYears;
        this.manYears = manYears;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DisabilityGrade fromLabel(String label) {
        return EnumLabelSupport.fromLabel(DisabilityGrade.class, DisabilityGrade::getLabel, label);
    }

    /**
     * Anos de contribuição exigidos neste grau, para o sexo informado.
     *
     * <p>A LC 142/2013 tem duas colunas, mulher e homem — não há linha para
     * "não-binário" nem "outro". Cair no masculino por omissão seria um
     * arbítrio jurídico invisível: o número sairia certo na tela e errado no
     * processo. Então aqui recusa, e quem chama decide o que dizer a quem
     * cadastrou.
     */
    public int requiredYears(Gender gender) {
        if (gender == Gender.FEMININO) return womanYears;
        if (gender == Gender.MASCULINO) return manYears;
        throw new IllegalArgumentException(
                "a conversão da LC 142/2013 só tem base para masculino ou feminino; recebido: "
                        + gender);
    }

    /** Se existe base de conversão para este sexo — ver {@link #requiredYears}. */
    public static boolean hasBasisFor(Gender gender) {
        return gender == Gender.FEMININO || gender == Gender.MASCULINO;
    }

    public int getWomanYears() {
        return womanYears;
    }

    public int getManYears() {
        return manYears;
    }

    /** Os três graus que podem ser gravados num intervalo — exclui o "sem deficiência". */
    public static List<DisabilityGrade> asDisability() {
        return List.of(GRAVE, MODERADA, LEVE);
    }

    /** Falso apenas para {@link #SEM_DEFICIENCIA}. */
    public boolean isDisability() {
        return this != SEM_DEFICIENCIA;
    }

    @Override
    public String toString() {
        return label;
    }
}
