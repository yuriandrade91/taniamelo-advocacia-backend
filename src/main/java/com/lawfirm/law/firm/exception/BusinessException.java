package com.lawfirm.law.firm.exception;

/**
 * Violação de regra de negócio (HTTP 422). Erro "esperado": a requisição é válida na forma, mas o
 * estado do domínio não permite a operação. Logado como WARN pela central de erros - nunca como
 * erro de sistema.
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode) {
        super(errorCode != null ? errorCode.getMessage() : null);
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, String message) {
        super(resolveMessage(message, errorCode));
        this.errorCode = errorCode;
    }

    private static String resolveMessage(String message, BusinessErrorCode errorCode) {
        if (message != null) {
            return message;
        }
        return errorCode != null ? errorCode.getMessage() : null;
    }

    public BusinessErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode != null ? errorCode.getCode() : "BUSINESS_RULE";
    }
}
