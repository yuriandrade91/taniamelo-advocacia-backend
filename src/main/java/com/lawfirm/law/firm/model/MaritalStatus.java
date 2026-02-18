package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.text.Normalizer;

public enum MaritalStatus {
    SOLTEIRO("Solteiro(a)"),
    CASADO("Casado(a)"),
    SEPARADO("Separado(a)"),
    DIVORCIADO("Divorciado(a)"),
    VIUVO("Viúvo(a)");

    private final String label;

    MaritalStatus(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static MaritalStatus fromLabel(String label) {
        if (label == null) throw new IllegalArgumentException("Unknown marital status: null");

        for (MaritalStatus s : values()) {
            if (s.label.equalsIgnoreCase(label)) return s;
        }

        String cleanLabel = normalize(label);
        for (MaritalStatus s : values()) {
            if (normalize(s.label).equals(cleanLabel)) return s;
        }

        throw new IllegalArgumentException("Unknown marital status: " + label);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String noParen = s.replaceAll("\\(.*?\\)", "");
        return Normalizer.normalize(noParen, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "")
                .toLowerCase();
    }

    @Override
    public String toString() { return label; }
}
