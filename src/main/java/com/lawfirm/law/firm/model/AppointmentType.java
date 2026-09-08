package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/** Tipo de compromisso na agenda. Persistido pelo label via converter. */
public enum AppointmentType {
    ENTREVISTA("Entrevista"),
    REUNIAO("Reunião"),
    PERICIA("Perícia"),
    AUDIENCIA("Audiência"),
    PRAZO("Prazo"),
    OUTRO("Outro");

    private final String label;

    AppointmentType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static AppointmentType fromLabel(String label) {
        return EnumLabelSupport.fromLabel(AppointmentType.class, AppointmentType::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
