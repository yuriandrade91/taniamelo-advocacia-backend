package com.lawfirm.law.firm.exception;

public enum SystemErrorCode {
    DATABASE_CONNECTION_ERROR("Não foi possível conectar ao banco de dados"),
    DATABASE_TIMEOUT("Tempo de espera esgotado ao comunicar com o banco de dados"),
    DATABASE_INTEGRITY_ERROR("Violação de integridade no banco de dados"),
    NETWORK_ERROR("Erro de rede ao comunicar com serviço externo"),
    TIMEOUT_ERROR("Tempo de execução excedido"),
    SERVICE_UNAVAILABLE("Serviço temporariamente indisponível"),
    AUTHENTICATION_ERROR("Falha na autenticação do usuário"),
    AUTHORIZATION_ERROR("Usuário não possui permissão para executar essa ação"),
    NULL_POINTER_ERROR("Erro interno: referência nula inesperada"),
    ILLEGAL_ARGUMENT_ERROR("Argumento inválido fornecido"),
    CONVERSION_ERROR("Falha ao converter dados entre formatos"),
    CONFIGURATION_ERROR("Erro de configuração do sistema"),
    RESOURCE_NOT_FOUND("Recurso solicitado não encontrado"),
    SECURITY_ERROR("Erro relacionado à segurança"),
    ENCRYPTION_ERROR("Erro durante operação de criptografia"),
    SYSTEM_ERROR("Erro interno do sistema"),
    UNKNOWN_ERROR("Erro desconhecido");

    private final String message;

    SystemErrorCode(String message) {
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
