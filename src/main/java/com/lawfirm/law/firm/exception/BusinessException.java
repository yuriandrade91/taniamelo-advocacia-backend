package com.lawfirm.law.firm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    // fixed business error code
    private final String code = "BUSINESS_RULE";

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode) {
        super(errorCode != null ? errorCode.getMessage() : null);
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, String message) {
        super(message != null ? message : (errorCode != null ? errorCode.getMessage() : null));
        this.errorCode = errorCode;
    }

    public BusinessException(BusinessErrorCode errorCode, String message, Throwable cause) {
        super(message != null ? message : (errorCode != null ? errorCode.getMessage() : null), cause);
        this.errorCode = errorCode;
    }

    public BusinessErrorCode getBusinessErrorCode() { return errorCode; }

    public String getCode() { return code; }
}
