package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/** Modalidade do compromisso (presencial ou online). Persistido pelo label via converter. */
public enum AppointmentModality {
    PRESENCIAL("Presencial"),
    ONLINE("Online");

    private final String label;

    AppointmentModality(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static AppointmentModality fromLabel(String label) {
        return EnumLabelSupport.fromLabel(
                AppointmentModality.class, AppointmentModality::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
