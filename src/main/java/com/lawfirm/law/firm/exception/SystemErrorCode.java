package com.lawfirm.law.firm.exception;

/**
 * Códigos de erro de sistema com mensagens humanizadas (PT-BR).
 * Adicione novas constantes conforme necessário.
 */
public enum SystemErrorCode {
    DATABASE_INTEGRITY_ERROR("Violação de integridade no banco de dados"),
    SYSTEM_ERROR("Erro interno do sistema");

    private final String message;

    SystemErrorCode(String message) {
        this.message = message;
    }

    public String getCode() { return name(); }

    public String getMessage() { return message; }

    @Override
    public String toString() { return name() + ": " + message; }
}
