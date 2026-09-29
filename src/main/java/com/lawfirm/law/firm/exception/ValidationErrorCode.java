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
    DUPLICATE_VALUE("Valor duplicado para campo único"),
    /**
     * Intervalo de datas impossível ou ambíguo — inclui pedir competência e caixa na mesma
     * consulta, que não tem resposta certa.
     */
    INVALID_DATE_RANGE("Intervalo de datas inválido"),

    /**
     * Dois parâmetros que se contradizem na mesma requisição. Escolher um por conta própria
     * devolveria uma resposta plausível para uma pergunta que ninguém fez.
     */
    CONFLICTING_PARAMETERS("Parâmetros conflitantes");

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
