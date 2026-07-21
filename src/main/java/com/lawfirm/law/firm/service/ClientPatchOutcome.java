package com.lawfirm.law.firm.service;

/** Resultado de um PATCH parcial do cliente - quais campos efetivamente mudaram. */
public record ClientPatchOutcome(
        boolean situationChanged,
        boolean benefitChanged,
        boolean clientTypeChanged,
        boolean notBillableChanged) {}
