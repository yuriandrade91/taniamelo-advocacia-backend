package com.lawfirm.law.firm.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.storage.FileStorageException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Maps DB column names to API field names for integrity-violation messages. */
    private static final Map<String, String> COLUMN_TO_FIELD =
            Map.of(
                    "cpf", "cpf",
                    "rg", "rg",
                    "email", "email",
                    "mobile_phone", "mobilePhone",
                    "beneficiary_number", "beneficiaryNumber",
                    "benefit_number", "beneficiaryNumber",
                    "nit_pis", "nitPis",
                    "ctps", "ctps",
                    "ctps_series", "ctpsSeries",
                    "reference_phone", "referencePhone");

    /**
     * Maps technical field names (DTO/entity) to human-friendly Portuguese labels. Used for
     * validation responses.
     */
    private static final Map<String, String> FIELD_LABELS =
            Map.ofEntries(
                    Map.entry("inssPassword", "Senha do INSS"),
                    Map.entry("motherName", "Nome da mãe"),
                    Map.entry("situation", "Situação"),
                    Map.entry("fullName", "Nome completo"),
                    Map.entry("mobilePhone", "Telefone celular"),
                    Map.entry("benefit", "Benefício"),
                    Map.entry("birthDate", "Data de nascimento"),
                    Map.entry("cpf", "CPF"),
                    Map.entry("amount", "Valor"));

    /** Matches the key column inside Postgres "Key (column)=(value)" messages. */
    private static final Pattern KEY_PATTERN = Pattern.compile("Key \\(([^)]+)\\)");

    // ── Bean-validation (@Valid) ──

    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        return handleBeanValidation(ex);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException ex) {
        List<ApiError> errors =
                ex.getParameterValidationResults().stream()
                        .filter(
                                org.springframework.validation.method.ParameterErrors.class
                                        ::isInstance)
                        .map(org.springframework.validation.method.ParameterErrors.class::cast)
                        .flatMap(result -> result.getFieldErrors().stream())
                        .map(
                                fieldError -> {
                                    String field = leafField(fieldError.getField());
                                    String label = FIELD_LABELS.getOrDefault(field, field);
                                    String message =
                                            humanizeMessage(label, fieldError.getDefaultMessage());
                                    return new ApiError(field, message, "VALIDATION_ERROR");
                                })
                        .toList();
        return badRequest(
                errors.isEmpty()
                        ? List.of(new ApiError(null, "Validação inválida", "VALIDATION_ERROR"))
                        : errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBeanValidation(
            MethodArgumentNotValidException ex) {
        List<ApiError> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fe -> {
                                    String technical = fe.getField();
                                    String field = leafField(technical);
                                    String label = FIELD_LABELS.getOrDefault(field, field);
                                    String msg = humanizeMessage(label, fe.getDefaultMessage());
                                    return new ApiError(field, msg, "VALIDATION_ERROR");
                                })
                        .toList();
        return badRequest(errors);
    }

    // ── Constraint violations (path-variable / @Validated) ──

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiError> errors =
                ex.getConstraintViolations().stream()
                        .map(
                                cv -> {
                                    String path = cv.getPropertyPath().toString();
                                    String field = leafField(path);
                                    String label = FIELD_LABELS.getOrDefault(field, field);
                                    String msg = humanizeMessage(label, cv.getMessage());
                                    return new ApiError(field, msg, "VALIDATION_ERROR");
                                })
                        .toList();
        return badRequest(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String field = ex.getName() != null ? ex.getName() : "parameter";
        String value = ex.getValue() == null ? "nulo" : ex.getValue().toString();
        String message = "Valor inválido para " + field + ": " + value;
        return badRequest(List.of(new ApiError(field, message, "INVALID_PARAMETER")));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception ex) {
        String field;
        String message;
        if (ex instanceof MissingServletRequestParameterException parameterException) {
            field = parameterException.getParameterName();
            message = "Parâmetro obrigatório ausente: " + field;
        } else {
            MissingServletRequestPartException partException =
                    (MissingServletRequestPartException) ex;
            field = partException.getRequestPartName();
            message = "Parte obrigatória ausente: " + field;
        }
        return badRequest(List.of(new ApiError(field, message, "REQUIRED_FIELD")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(
            HttpMediaTypeNotSupportedException ex) {
        return respond(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                List.of(new ApiError(null, ex.getMessage(), "UNSUPPORTED_MEDIA_TYPE")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex) {
        return badRequest(
                List.of(
                        new ApiError(
                                "files",
                                "Arquivo excedeu o tamanho máximo permitido.",
                                "FILE_TOO_LARGE")));
    }

    // ── Custom validation exceptions ──

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomValidation(ValidationException ex) {
        String code =
                ex.getValidationErrorCode() != null
                        ? ex.getValidationErrorCode().getCode()
                        : ex.getCode();
        return badRequest(List.of(new ApiError(ex.getField(), ex.getMessage(), code)));
    }

    // ── Business rule violations ──

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        String code = ex.getCode();
        return respond(
                HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError(null, ex.getMessage(), code)));
    }

    // ── Authentication / authorization ──

    @ExceptionHandler({AuthenticationException.class, AuthenticationServiceException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(Exception ex) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                List.of(new ApiError(null, "Credenciais inválidas", "INVALID_CREDENTIALS")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(
                HttpStatus.FORBIDDEN,
                List.of(new ApiError(null, "Acesso negado", "ACCESS_DENIED")));
    }

    // ── Not found ──

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return respond(
                HttpStatus.NOT_FOUND, List.of(new ApiError(null, ex.getMessage(), "NOT_FOUND")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        String path = ex.getResourcePath() != null ? ex.getResourcePath() : "rota";
        return respond(
                HttpStatus.NOT_FOUND,
                List.of(new ApiError(null, "Rota não encontrada: " + path, "NOT_FOUND")));
    }

    // ── DB unique-constraint violations ──

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        String detail = rootMessage(ex);
        Matcher m = KEY_PATTERN.matcher(detail);
        if (m.find()) {
            String column = m.group(1).trim();
            String field = COLUMN_TO_FIELD.getOrDefault(column, column);
            return respond(
                    HttpStatus.CONFLICT,
                    List.of(
                            new ApiError(
                                    field, "Valor duplicado para campo único", "DUPLICATE_VALUE")));
        }
        return respond(
                HttpStatus.CONFLICT,
                List.of(
                        new ApiError(
                                null,
                                SystemErrorCode.DATABASE_INTEGRITY_ERROR.getMessage(),
                                SystemErrorCode.DATABASE_INTEGRITY_ERROR.getCode())));
    }

    // ── Malformed JSON body ──

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        if (cause instanceof InvalidFormatException invalidFormat) {
            String field =
                    invalidFormat.getPath().stream()
                            .map(JsonMappingException.Reference::getFieldName)
                            .filter(java.util.Objects::nonNull)
                            .reduce((first, ignored) -> ignored)
                            .orElse(null);
            String value =
                    invalidFormat.getValue() == null ? "nulo" : invalidFormat.getValue().toString();
            String message =
                    "Valor inválido para " + (field != null ? field : "campo") + ": " + value;
            return badRequest(
                    List.of(
                            new ApiError(
                                    field,
                                    message,
                                    ValidationErrorCode.INVALID_ENUM_VALUE.getCode())));
        }
        String msg = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        return badRequest(List.of(new ApiError(null, msg, "INVALID_REQUEST_BODY")));
    }

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleJpaSystem(JpaSystemException ex) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage() != null ? root.getMessage() : ex.getMessage();
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(new ApiError(null, "Persistence error: " + msg, "PERSISTENCE_ERROR")));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystem(SystemException ex) {
        String code = ex.getCode();
        String message =
                ex.getErrorCode() != null
                        ? ex.getErrorCode().getMessage()
                        : SystemErrorCode.SYSTEM_ERROR.getMessage();
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR, List.of(new ApiError(null, message, code)));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorage(FileStorageException ex) {
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(
                        new ApiError(
                                null,
                                SystemErrorCode.FILE_STORAGE_ERROR.getMessage(),
                                SystemErrorCode.FILE_STORAGE_ERROR.getCode())));
    }

    // ── Catch-all ──

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(
                        new ApiError(
                                null,
                                SystemErrorCode.SYSTEM_ERROR.getMessage(),
                                SystemErrorCode.SYSTEM_ERROR.getCode())));
    }

    // ── Helpers ──

    private static ResponseEntity<ApiResponse<Void>> badRequest(List<ApiError> errors) {
        return respond(HttpStatus.BAD_REQUEST, errors);
    }

    private static ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, List<ApiError> errors) {
        return ResponseEntity.status(status).body(ApiResponse.error(errors));
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : "";
    }

    private static String leafField(String path) {
        if (path == null || path.isBlank()) return path;
        String sanitized = path.replaceAll("\\[[^\\]]*\\]", "");
        int lastDot = sanitized.lastIndexOf('.');
        if (lastDot >= 0) return sanitized.substring(lastDot + 1);
        return sanitized;
    }

    /**
     * Converts a technical validation message into a human-friendly Portuguese sentence using the
     * provided label. Examples: - (label="Nome", defaultMsg="must not be blank") -> "Por favor,
     * informe o Nome." - (label="Data de nascimento", defaultMsg="must not be null") -> "Por favor,
     * informe a Data de nascimento."
     */
    private static String humanizeMessage(String label, String defaultMsg) {
        if (defaultMsg == null) return "Por favor, verifique o campo: " + label + ".";
        String msg = defaultMsg.toLowerCase();
        if (msg.contains("not be blank")
                || msg.contains("não pode estar em branco")
                || msg.contains("must not be blank")) {
            return "Por favor, informe " + label + ".";
        }
        if (msg.contains("not be null")
                || msg.contains("must not be null")
                || msg.contains("não pode ser nulo")) {
            return "Por favor, informe " + label + ".";
        }
        if (msg.contains("invalid") || msg.contains("inválido")) {
            return "Valor inválido para " + label + ".";
        }
        // fallback: return the original message but prefixed politely
        return defaultMsg.substring(0, 1).toUpperCase() + defaultMsg.substring(1);
    }
}
