package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

public enum PaymentMethod {
    PIX("Pix"),
    BOLETO("Boleto"),
    DINHEIRO("Dinheiro"),
    TRANSFERENCIA("Transferência"),
    CARTAO("Cartão"),
    OUTRO("Outro");

    private final String label;

    PaymentMethod(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static PaymentMethod fromLabel(String label) {
        return EnumLabelSupport.fromLabel(PaymentMethod.class, PaymentMethod::getLabel, label);
    }

    @Override
    public String toString() { return label; }
}
