package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/** Situação (etapa) do cliente no fluxo do escritório. Persistida pelo label via converter. */
public enum Situation {
    FORMULARIO_PREENCHIDO("Formulário preenchido"),
    ANALISE_DOCUMENTAL("Análise documental"),
    PLANEJAMENTO_EM_EXECUCAO("Planejamento em execução"),
    PLANEJAMENTO_CONCLUIDO("Planejamento concluído"),
    BENEFICIO_FUTURO("Benefício futuro"),
    BENEFICIO_CONCLUIDO("Benefício concluído");

    private final String label;

    Situation(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Situation fromLabel(String label) {
        return EnumLabelSupport.fromLabel(Situation.class, Situation::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
