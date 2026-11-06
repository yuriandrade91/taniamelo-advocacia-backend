package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

public enum MaritalStatus {
    SOLTEIRO("Solteiro(a)"),
    CASADO("Casado(a)"),
    SEPARADO("Separado(a)"),
    DIVORCIADO("Divorciado(a)"),
    VIUVO("Viúvo(a)");

    private final String label;

    MaritalStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static MaritalStatus fromLabel(String label) {
        return EnumLabelSupport.fromLabel(MaritalStatus.class, MaritalStatus::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
