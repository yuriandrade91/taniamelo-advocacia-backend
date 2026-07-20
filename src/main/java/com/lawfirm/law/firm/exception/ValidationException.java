package com.lawfirm.law.firm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception to represent a validation error for a specific field. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    // fixed validation error code
    private final String code = "VALIDATION_ERROR";

    // field that caused the validation error
    private final String field;

    private final ValidationErrorCode validationErrorCode;

    public ValidationException(String field, ValidationErrorCode validationErrorCode) {
        super(validationErrorCode != null ? validationErrorCode.getMessage() : null);
        this.field = field;
        this.validationErrorCode = validationErrorCode;
    }

    public ValidationException(
            String field, ValidationErrorCode validationErrorCode, String message) {
        super(
                message != null
                        ? message
                        : (validationErrorCode != null ? validationErrorCode.getMessage() : null));
        this.field = field;
        this.validationErrorCode = validationErrorCode;
    }

    public ValidationException(
            String field,
            ValidationErrorCode validationErrorCode,
            String message,
            Throwable cause) {
        super(
                message != null
                        ? message
                        : (validationErrorCode != null ? validationErrorCode.getMessage() : null),
                cause);
        this.field = field;
        this.validationErrorCode = validationErrorCode;
    }

    public String getField() {
        return field;
    }

    public String getCode() {
        return code;
    }

    public ValidationErrorCode getValidationErrorCode() {
        return validationErrorCode;
    }
}
