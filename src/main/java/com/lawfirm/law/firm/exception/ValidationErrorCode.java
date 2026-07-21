package com.lawfirm.law.firm.exception;

/** Códigos de erro de validação com mensagens humanizadas (PT-BR). */
public enum ValidationErrorCode {
    INVALID_CPF("CPF inválido"),
    INVALID_EMAIL("E-mail em formato inválido"),
    REQUIRED_FIELD("Campo obrigatório não informado"),
    INVALID_DATE("Data em formato inválido"),
    INVALID_PHONE("Telefone em formato inválido"),
    INVALID_ENUM_VALUE("Valor de enum inválido"),
    INVALID_SITUATION("Situação inválida"),
    INVALID_BENEFIT("Benefício inválido"),
    INVALID_CLIENT_TYPE("Tipo de cliente inválido"),
    DUPLICATE_VALUE("Valor duplicado para campo único");

    private final String message;

    ValidationErrorCode(String message) {
        this.message = message;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return name() + ": " + message;
    }
}
