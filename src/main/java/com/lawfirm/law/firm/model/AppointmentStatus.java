package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/** Situação do compromisso na agenda. Persistido pelo label via converter. */
public enum AppointmentStatus {
    AGENDADO("Agendado"),
    CONCLUIDO("Concluído"),
    CANCELADO("Cancelado");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static AppointmentStatus fromLabel(String label) {
        return EnumLabelSupport.fromLabel(
                AppointmentStatus.class, AppointmentStatus::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
