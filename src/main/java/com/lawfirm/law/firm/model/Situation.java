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
        for (Situation s : values()) {
            if (s.label.equalsIgnoreCase(label)) return s;
        }
        throw new IllegalArgumentException("Unknown situation: " + label);
    }

    @Override
    public String toString() { return label; }
}
