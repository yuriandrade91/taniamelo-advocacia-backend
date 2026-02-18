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

        // First try exact (case-insensitive) match
        for (MaritalStatus s : values()) {
            if (s.label.equalsIgnoreCase(label)) return s;
        }

        // Normalize and try tolerant matching (remove accents/parentheses, compare base stems)
        String cleanLabel = normalize(label);
        for (MaritalStatus s : values()) {
            String candidate = normalize(s.label);
            if (candidate.equals(cleanLabel)) return s;

            // Allow final-letter variations (e.g. 'Separado' vs 'Separada') by comparing stems
            if (candidate.length() > 1 && cleanLabel.length() > 1) {
                int min = Math.min(candidate.length(), cleanLabel.length());
                if (candidate.substring(0, min - 1).equals(cleanLabel.substring(0, min - 1))) return s;
            }
        }

        throw new IllegalArgumentException("Unknown marital status: " + label);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        // remove parenthesis and their contents, normalize accents, remove non-letters, lowercase
        String noParen = s.replaceAll("\\(.*?\\)", "");
        String normalized = Normalizer.normalize(noParen, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^A-Za-z]", "").toLowerCase();
    }

    @Override
    public String toString() { return label; }
}
