package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.text.Normalizer;

public enum BenefitType {
    APOSENTADORIA_POR_IDADE("Aposentadoria por idade"),
    APOSENTADORIA_POR_TEMPO_CONTRIBUICAO("Aposentadoria por tempo de contribuição"),
    APOSENTADORIA_POR_INCAPACIDADE_PERMANENTE("Aposentadoria por incapacidade permanente"),
    APOSENTADORIA_ESPECIAL("Aposentadoria especial"),
    APOSENTADORIA_POR_DEFICIENCIA("Aposentadoria por deficiência"),
    APOSENTADORIA_POR_TEMPO_PROFESSOR("Aposentadoria por tempo de contribuição do professor"),
    APOSENTADORIA_POR_INVALIDEZ("Aposentadoria por invalidez");

    private final String label;
    BenefitType(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static BenefitType fromLabel(String label) {
        if (label == null) return null;

        String cleaned = label.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) return null;

        // try name match (allow spaces -> underscores)
        try {
            String candidate = cleaned.toUpperCase().replaceAll("\\s+", "_").replaceAll("[^A-Z0-9_]", "");
            return BenefitType.valueOf(candidate);
        } catch (IllegalArgumentException ignored) {
            // fallthrough
        }

        // direct case-insensitive name match
        for (BenefitType b : values()) {
            if (b.name().equalsIgnoreCase(cleaned)) return b;
        }

        // match against label (case-insensitive)
        for (BenefitType b : values()) {
            if (b.label.equalsIgnoreCase(cleaned)) return b;
        }

        // normalized comparison (remove diacritics, non-alnum, lowercase)
        String normalized = normalize(cleaned);
        for (BenefitType b : values()) {
            if (normalize(b.name()).equals(normalized) || normalize(b.label).equals(normalized)) return b;
        }

        throw new IllegalArgumentException("Unknown benefit type: " + label);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^\\p{Alnum}]+", "").toLowerCase();
    }

    @Override public String toString() { return label; }
}
