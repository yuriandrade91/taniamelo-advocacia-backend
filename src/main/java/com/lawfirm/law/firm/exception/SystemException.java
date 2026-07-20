package com.lawfirm.law.firm.exception;

/**
 * Erro de sistema/infraestrutura (HTTP 500): banco indisponível, storage
 * inacessível, falha inesperada. O detalhe técnico NUNCA vaza para o cliente
 * da API - vai para o log (ERROR) com stack trace; a resposta carrega só o
 * código e uma mensagem genérica.
 */
public class SystemException extends RuntimeException {

    private final SystemErrorCode errorCode;

    public SystemException(SystemErrorCode errorCode, Throwable cause) {
        super(errorCode != null ? errorCode.getMessage() : null, cause);
        this.errorCode = errorCode;
    }

    public SystemException(SystemErrorCode errorCode, String internalDetail, Throwable cause) {
        super(internalDetail, cause);
        this.errorCode = errorCode;
    }

    public SystemErrorCode getErrorCode() { return errorCode; }

    public String getCode() {
        return errorCode != null ? errorCode.getCode() : "SYSTEM_ERROR";
    }
}
