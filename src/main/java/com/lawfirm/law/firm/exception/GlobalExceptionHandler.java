package com.lawfirm.law.firm.exception;

import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.orm.jpa.JpaSystemException;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Maps DB column names to API field names for integrity-violation messages. */
    private static final Map<String, String> COLUMN_TO_FIELD = Map.of(
            "cpf", "cpf",
            "rg", "rg",
            "email", "email",
            "mobile_phone", "mobilePhone",
            "beneficiary_number", "beneficiaryNumber",
            "nit_pis", "nitPis",
            "ctps", "ctps",
            "ctps_series", "ctpsSeries",
            "reference_phone", "referencePhone"
    );

    /** Matches the key column inside Postgres "Key (column)=(value)" messages. */
    private static final Pattern KEY_PATTERN = Pattern.compile("Key \\(([^)]+)\\)");

    // ── Bean-validation (@Valid) ──

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError(fe.getField(), fe.getDefaultMessage(), "VALIDATION_ERROR"))
                .toList();
        return badRequest(errors);
    }

    // ── Constraint violations (path-variable / @Validated) ──

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiError> errors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return new ApiError(field, cv.getMessage(), "VALIDATION_ERROR");
                })
                .toList();
        return badRequest(errors);
    }

    // ── Custom validation exceptions ──

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomValidation(ValidationException ex) {
        String code = ex.getValidationErrorCode() != null
                ? ex.getValidationErrorCode().getCode()
                : ex.getCode();
        return badRequest(List.of(new ApiError(ex.getField(), ex.getMessage(), code)));
    }

    // ── Business rule violations ──

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        String code = ex.getBusinessErrorCode() != null
                ? ex.getBusinessErrorCode().getCode()
                : ex.getCode();
        return badRequest(List.of(new ApiError(null, ex.getMessage(), code)));
    }

    // ── Not found ──

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND,
                List.of(new ApiError(null, ex.getMessage(), "NOT_FOUND")));
    }

    // ── DB unique-constraint violations ──

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = rootMessage(ex);
        Matcher m = KEY_PATTERN.matcher(detail);
        if (m.find()) {
            String column = m.group(1).trim();
            String field = COLUMN_TO_FIELD.getOrDefault(column, column);
            return respond(HttpStatus.CONFLICT,
                    List.of(new ApiError(field, "Valor duplicado para campo único", "DUPLICATE_VALUE")));
        }
        return respond(HttpStatus.CONFLICT,
                List.of(new ApiError(null, SystemErrorCode.DATABASE_INTEGRITY_ERROR.getMessage(),
                        SystemErrorCode.DATABASE_INTEGRITY_ERROR.getCode())));
    }

    // ── Malformed JSON body ──

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        return badRequest(List.of(new ApiError(null, msg, "INVALID_REQUEST_BODY")));
    }

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleJpaSystem(JpaSystemException ex) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage() != null ? root.getMessage() : ex.getMessage();
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(new ApiError(null, "Persistence error: " + msg, "PERSISTENCE_ERROR")));
    }

    // ── Catch-all ──

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(new ApiError(null, SystemErrorCode.SYSTEM_ERROR.getMessage(),
                        SystemErrorCode.SYSTEM_ERROR.getCode())));
    }

    // ── Helpers ──

    private static ResponseEntity<ApiResponse<Void>> badRequest(List<ApiError> errors) {
        return respond(HttpStatus.BAD_REQUEST, errors);
    }

    private static ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, List<ApiError> errors) {
        return ResponseEntity.status(status).body(ApiResponse.error(errors));
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : "";
    }
}
