package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
        
        // Try exact case-insensitive match first
        for (Situation s : values()) {
            if (s.label.equalsIgnoreCase(label)) return s;
        }
        
        // Fallback: try normalized (remove accents) match for legacy data
        String normalizedInput = removeAccents(label).toLowerCase();
        for (Situation s : values()) {
            String normalizedLabel = removeAccents(s.label).toLowerCase();
            if (normalizedLabel.equals(normalizedInput)) return s;
        }
        
        throw new IllegalArgumentException("Unknown situation: " + label);
    }
    
    // Helper to remove accents for legacy data compatibility
    private static String removeAccents(String input) {
        return input.replaceAll("[àáâãäå]", "a")
                   .replaceAll("[èéêë]", "e")
                   .replaceAll("[ìíîï]", "i")
                   .replaceAll("[òóôõö]", "o")
                   .replaceAll("[ùúûü]", "u")
                   .replaceAll("[ç]", "c");
    }

    @Override public String toString() { return label; }
}
