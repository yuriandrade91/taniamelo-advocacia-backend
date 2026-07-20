package com.lawfirm.law.firm.exception;

/** Códigos de erro de sistema (HTTP 500) com mensagens seguras para o cliente da API. */
public enum SystemErrorCode {
    DATABASE_INTEGRITY_ERROR("Violação de integridade no banco de dados"),
    FILE_STORAGE_ERROR("Falha ao processar arquivo no storage"),
    SYSTEM_ERROR("Erro interno do sistema. Tente novamente; se persistir, contate o suporte.");

    private final String message;

    SystemErrorCode(String message) {
        this.message = message;
    }

    public String getCode() { return name(); }

    public String getMessage() { return message; }

    @Override
    public String toString() { return name() + ": " + message; }
}
