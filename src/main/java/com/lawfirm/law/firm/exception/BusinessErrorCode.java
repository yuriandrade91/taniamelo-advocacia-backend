package com.lawfirm.law.firm.exception;

/** Códigos de erro de negócio (HTTP 422) com mensagens humanizadas (PT-BR). */
public enum BusinessErrorCode {
    OPERATION_NOT_ALLOWED("Operação não permitida"),
    BUSINESS_RULE_VIOLATION("Violação de regra de negócio");

    private final String message;

    BusinessErrorCode(String message) {
        this.message = message;
    }

    public String getCode() { return name(); }

    public String getMessage() { return message; }

    @Override
    public String toString() { return name() + ": " + message; }
}
