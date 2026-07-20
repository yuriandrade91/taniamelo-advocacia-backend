package com.lawfirm.law.firm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lawfirm.law.firm.util.EnumLabelSupport;

/**
 * Status persistido de uma parcela. "Atrasado" NÃO é um status: é derivado em
 * runtime (Pendente + due_date no passado) - ver ClientPaymentService.
 */
public enum PaymentStatus {
    PENDENTE("Pendente"),
    PAGO("Pago"),
    CANCELADO("Cancelado");

    private final String label;

    PaymentStatus(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static PaymentStatus fromLabel(String label) {
        return EnumLabelSupport.fromLabel(PaymentStatus.class, PaymentStatus::getLabel, label);
    }

    @Override
    public String toString() { return label; }
}
