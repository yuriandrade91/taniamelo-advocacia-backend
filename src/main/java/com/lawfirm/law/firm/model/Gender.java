package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Gender {
    Masculino("Masculino"),
    Feminino("Feminino"),
    NaoBinario("Não-binário"),
    Outro("Outro");

    private final String label;
    Gender(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static Gender fromLabel(String label) {
        for (Gender g : values()) {
            if (g.label.equalsIgnoreCase(label)) return g;
        }
        throw new IllegalArgumentException("Unknown gender: " + label);
    }

    @Override public String toString() { return label; }
}
