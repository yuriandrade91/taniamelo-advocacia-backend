package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/** Tipo do endereço na coleção 1:N por cliente (um deles marcado como principal). */
public enum AddressType {
    RESIDENCIAL("Residencial"),
    COMERCIAL("Comercial"),
    CORRESPONDENCIA("Correspondência");

    private final String label;

    AddressType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static AddressType fromLabel(String label) {
        return EnumLabelSupport.fromLabel(AddressType.class, AddressType::getLabel, label);
    }

    @Override
    public String toString() {
        return label;
    }
}
