package com.lawfirm.law.firm.exception;

/**
 * Códigos de erro de negócio com mensagens humanizadas (PT-BR).
 */
public enum BusinessErrorCode {
    USER_ALREADY_EXISTS("Usuário já existe na base", new String[] {"cpf", "email"}),
    INSUFFICIENT_CONTRIBUTION_TIME("Tempo de contribuição insuficiente para este benefício", new String[] {"contributionTime"}),
    BENEFIT_ALREADY_REGISTERED("Benefício já cadastrado para este CPF", new String[] {"cpf", "benefit"}),
    CLIENT_NOT_ELIGIBLE("Cliente não elegível para o benefício", new String[] {"cpf", "benefit"}),
    OPERATION_NOT_ALLOWED("Operação não permitida", new String[0]),
    DUPLICATE_RECORD("Registro duplicado", new String[] {"cpf", "rg", "benefitNumber"}),
    BUSINESS_RULE_VIOLATION("Violação de regra de negócio", new String[0]);

    private final String message;
    private final String[] requiredFields;

    BusinessErrorCode(String message, String[] requiredFields) {
        this.message = message;
        this.requiredFields = requiredFields != null ? requiredFields : new String[0];
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }

    /**
     * Campos que são considerados obrigatórios / relevantes para este erro de negócio.
     * São nomes de propriedades do payload/DTO (ex.: "cpf", "benefit").
     */
    public String[] getRequiredFields() {
        return requiredFields.clone();
    }

    @Override
    public String toString() {
        return name() + ": " + message;
    }
}
