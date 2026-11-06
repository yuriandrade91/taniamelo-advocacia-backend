package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/**
 * Tipo de benefício previdenciário pleiteado. Persistido pelo label via converter.
 *
 * <p>Eram nove, e dois pares eram o mesmo benefício com dois nomes: "invalidez" é o nome que a lei
 * usava antes da EC 103/2019 para o que hoje se chama <b>incapacidade permanente</b>, e "PCD" é o
 * jeito informal de dizer <b>deficiência</b>. Como o banco guarda o label, o mesmo benefício ficava
 * gravado de dois jeitos - e qualquer contagem por benefício somava metade em cada linha, sem
 * ninguém notar que os dois eram um.
 *
 * <p>Ficaram os sete canônicos. Os dois nomes antigos continuam sendo <b>aceitos na entrada</b>
 * (ver {@link #APELIDOS}), porque payload antigo e integração de fora não têm de quebrar - mas o
 * que se grava e o que volta na resposta é o canônico.
 */
public enum BenefitType {
    APOSENTADORIA_POR_IDADE("Aposentadoria por idade"),
    APOSENTADORIA_POR_TEMPO_CONTRIBUICAO("Aposentadoria por tempo de contribuição"),
    APOSENTADORIA_POR_INCAPACIDADE_PERMANENTE("Aposentadoria por incapacidade permanente"),
    APOSENTADORIA_ESPECIAL("Aposentadoria especial"),
    APOSENTADORIA_POR_DEFICIENCIA("Aposentadoria por deficiência"),
    APOSENTADORIA_POR_TEMPO_PROFESSOR("Aposentadoria por tempo de contribuição do professor"),
    APOSENTADORIA_RURAL("Aposentadoria rural");

    /**
     * Escritas antigas que resolvem para o benefício canônico.
     *
     * <p>A chave é normalizada por {@link EnumLabelSupport#normalize}, que tira acento, espaço,
     * pontuação e caixa - por isso "APOSENTADORIA_POR_INVALIDEZ" e "Aposentadoria por invalidez"
     * caem na mesma entrada, e uma linha cobre as duas escritas.
     */
    private static final java.util.Map<String, BenefitType> APELIDOS =
            java.util.Map.of(
                    EnumLabelSupport.normalize("Aposentadoria por invalidez"),
                    APOSENTADORIA_POR_INCAPACIDADE_PERMANENTE,
                    EnumLabelSupport.normalize("APOSENTADORIA_PCD"),
                    APOSENTADORIA_POR_DEFICIENCIA,
                    EnumLabelSupport.normalize("Aposentadoria para PCD"),
                    APOSENTADORIA_POR_DEFICIENCIA);

    private final String label;

    BenefitType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static BenefitType fromLabel(String label) {
        return EnumLabelSupport.fromLabel(
                BenefitType.class, BenefitType::getLabel, label, APELIDOS);
    }

    @Override
    public String toString() {
        return label;
    }
}
