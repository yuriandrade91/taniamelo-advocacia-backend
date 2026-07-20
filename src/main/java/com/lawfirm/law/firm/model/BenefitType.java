package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/** Tipo de benefício previdenciário pleiteado. Persistido pelo label via converter. */
public enum BenefitType {
    APOSENTADORIA_POR_IDADE("Aposentadoria por idade"),
    APOSENTADORIA_POR_TEMPO_CONTRIBUICAO("Aposentadoria por tempo de contribuição"),
    APOSENTADORIA_POR_INCAPACIDADE_PERMANENTE("Aposentadoria por incapacidade permanente"),
    APOSENTADORIA_ESPECIAL("Aposentadoria especial"),
    APOSENTADORIA_POR_DEFICIENCIA("Aposentadoria por deficiência"),
    APOSENTADORIA_POR_TEMPO_PROFESSOR("Aposentadoria por tempo de contribuição do professor"),
    APOSENTADORIA_POR_INVALIDEZ("Aposentadoria por invalidez"),
    APOSENTADORIA_RURAL("Aposentadoria rural"),
    APOSENTADORIA_PCD("Aposentadoria para PCD");

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
        return EnumLabelSupport.fromLabel(BenefitType.class, BenefitType::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
