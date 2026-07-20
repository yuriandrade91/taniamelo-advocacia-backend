package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

public enum ClientType {
    VERIFICADO("Verificado"),
    POTENCIAL("Potencial");

    private final String label;

    ClientType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ClientType fromLabel(String label) {
        return EnumLabelSupport.fromLabel(ClientType.class, ClientType::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
