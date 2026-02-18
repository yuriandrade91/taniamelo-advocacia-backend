package com.lawfirm.law.firm.exception;

/**
 * Códigos de erro de negócio com mensagens humanizadas (PT-BR).
 */
public enum BusinessErrorCode {
    USER_ALREADY_EXISTS("Usuário já existe na base"),
    INSUFFICIENT_CONTRIBUTION_TIME("Tempo de contribuição insuficiente para este benefício"),
    BENEFIT_ALREADY_REGISTERED("Benefício já cadastrado para este CPF"),
    CLIENT_NOT_ELIGIBLE("Cliente não elegível para o benefício"),
    OPERATION_NOT_ALLOWED("Operação não permitida"),
    DUPLICATE_RECORD("Registro duplicado"),
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
