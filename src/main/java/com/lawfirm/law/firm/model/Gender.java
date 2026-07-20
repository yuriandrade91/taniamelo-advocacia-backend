package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

public enum Gender {
    MASCULINO("Masculino"),
    FEMININO("Feminino"),
    NAO_BINARIO("Não-binário"),
    OUTRO("Outro");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Gender fromLabel(String label) {
        return EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
