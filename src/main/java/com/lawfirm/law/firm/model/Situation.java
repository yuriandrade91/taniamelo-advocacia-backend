package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.text.Normalizer;


public enum Situation {
    FORMULARIO_PREENCHIDO("Formulário preenchido"),
    ANALISE_DOCUMENTAL("Análise documental"),
    PLANEJAMENTO_EM_EXECUCAO("Planejamento em execução"),
    PLANEJAMENTO_CONCLUIDO("Planejamento concluído"),
    BENEFICIO_FUTURO("Benefício futuro"),
    BENEFICIO_CONCLUIDO("Benefício concluído");

    private final String label;

    Situation(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static Situation fromLabel(String label) {
        if (label == null) return null;

        String cleaned = label.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        if (cleaned.isEmpty()) return null;

        // try direct enum name with normalization (allow spaces -> underscores)
        try {
            String candidate = cleaned.toUpperCase().replaceAll("\\s+", "_").replaceAll("[^A-Z0-9_]", "");
            return Situation.valueOf(candidate);
        } catch (IllegalArgumentException ignored) {
            // fallthrough
        }

        // direct match against enum name (case-insensitive)
        for (Situation s : values()) {
            if (s.name().equalsIgnoreCase(cleaned)) return s;
        }

        // direct match against label
        for (Situation s : values()) {
            if (s.label.equalsIgnoreCase(cleaned)) return s;
        }

        // normalized comparison (remove diacritics, non-alnum, lowercase)
        String normalized = normalize(cleaned);
        for (Situation s : values()) {
            if (normalize(s.name()).equals(normalized) || normalize(s.label).equals(normalized)) return s;
        }

        throw new IllegalArgumentException("Unknown situation: " + label);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^\\p{Alnum}]+", "").toLowerCase();
    }

    // public wrapper for other classes that need normalized comparison
    public static String normalizeForComparison(String input) {
        return normalize(input);
    }

    @Override
    public String toString() { return label; }
}
