package com.lawfirm.law.firm.exception;

/**
 * Violação de regra de negócio (HTTP 422). Erro "esperado": a requisição é
 * válida na forma, mas o estado do domínio não permite a operação. Logado como
 * WARN pela central de erros - nunca como erro de sistema.
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode) {
        super(errorCode != null ? errorCode.getMessage() : null);
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, String message) {
        super(message != null ? message : (errorCode != null ? errorCode.getMessage() : null));
        this.errorCode = errorCode;
    }

    public BusinessErrorCode getErrorCode() { return errorCode; }

    public String getCode() {
        return errorCode != null ? errorCode.getCode() : "BUSINESS_RULE";
    }
}
