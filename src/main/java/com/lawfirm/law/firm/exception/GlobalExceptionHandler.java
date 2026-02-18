package com.lawfirm.law.firm.exception;

import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiError> errors = new ArrayList<>();
        for (FieldError f : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ApiError(f.getField(), f.getDefaultMessage(), "VALIDATION_ERROR"));
        }
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(v ->
            errors.add(new ApiError(v.getPropertyPath().toString(), v.getMessage(), "VALIDATION_ERROR"))
        );
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ValidationException.class)
    protected ResponseEntity<Object> handleValidationException(ValidationException ex) {
        String code = ex.getCode();
        String message = ex.getMessage();
        if (ex.getValidationErrorCode() != null) {
            code = ex.getValidationErrorCode().getCode();
            // prefer an explicit message passed in the exception; fall back to the enum message
            if (message == null || message.isBlank()) {
                message = ex.getValidationErrorCode().getMessage();
            }
        }
        List<ApiError> errors = List.of(new ApiError(ex.getField(), message, code));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<Object> handleBusiness(BusinessException ex) {
        String code = ex.getCode();
        String message = ex.getMessage();
        if (ex.getBusinessErrorCode() != null) {
            code = ex.getBusinessErrorCode().getCode();
            message = ex.getBusinessErrorCode().getMessage();
        }
        List<ApiError> errors = List.of(new ApiError(null, message, code));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        List<ApiError> errors = List.of(new ApiError(null, ex.getMessage(), "NOT_FOUND"));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());

        // Build a concatenated message from the exception and all causes to increase chance of finding column/constraint info
        StringBuilder all = new StringBuilder();
        if (ex.getMessage() != null) all.append(ex.getMessage()).append("; ");
        Throwable current = ex.getMostSpecificCause();
        while (current != null) {
            if (current.getMessage() != null) all.append(current.getMessage()).append("; ");
            current = current.getCause();
        }
        String specific = all.length() > 0 ? all.toString() : null;

        List<ApiError> errors = new ArrayList<>();

        if (specific != null) {
            // Try multiple patterns to extract constraint name or the column(s) mentioned from the aggregated message
            String extracted = null;
            java.util.regex.Pattern pConstraint = java.util.regex.Pattern.compile("constraint \"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pKey = java.util.regex.Pattern.compile("key \\(([^)]+)\\)\\s*=?", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pColumn = java.util.regex.Pattern.compile("column \"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Pattern pSimple = java.util.regex.Pattern.compile("\\b([a-z0-9_]+)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

            java.util.regex.Matcher m = pConstraint.matcher(specific);
            if (m.find()) {
                extracted = m.group(1);
            } else {
                m = pKey.matcher(specific);
                if (m.find()) {
                    extracted = m.group(1);
                } else {
                    m = pColumn.matcher(specific);
                    if (m.find()) {
                        extracted = m.group(1);
                    }
                }
            }

            // If still not found, try to find likely column-like tokens in the message
            if (extracted == null) {
                m = pSimple.matcher(specific);
                while (m.find()) {
                    String cand = m.group(1);
                    // ignore very short tokens
                    if (cand.length() > 2 && cand.matches("[a-z0-9_].*")) {
                        // prefer tokens containing known substrings
                        if (cand.toLowerCase().contains("ctps") || cand.toLowerCase().contains("benefit") || cand.toLowerCase().contains("nit") || cand.toLowerCase().contains("cpf") || cand.toLowerCase().contains("email") || cand.toLowerCase().contains("rg") || cand.toLowerCase().contains("mobile") || cand.toLowerCase().contains("reference")) {
                            extracted = cand;
                            break;
                        }
                    }
                }
            }

            if (extracted != null) {
                String token = extracted.split(",")[0].trim();
                token = token.replaceAll("=.*$", "").replaceAll("\\)\\z", "").trim();

                String field = mapDbColumnToField(token);
                if (field == null) field = mapDbColumnToField(specific);

                if (field != null) {
                    String userMessage;
                    switch (field) {
                        case "benefitNumber": userMessage = "Número do benefício já cadastrado"; break;
                        case "cpf": userMessage = "CPF já cadastrado"; break;
                        case "email": userMessage = "E-mail já cadastrado"; break;
                        case "mobilePhone": userMessage = "Telefone móvel já cadastrado"; break;
                        case "referencePhone": userMessage = "Telefone de referência já cadastrado"; break;
                        case "rg": userMessage = "RG já cadastrado"; break;
                        case "ctps": userMessage = "CTPS já cadastrado"; break;
                        case "nitPis": userMessage = "NIT/PIS já cadastrado"; break;
                        default: userMessage = "Valor duplicado para campo único"; break;
                    }
                    errors.add(new ApiError(field, userMessage, ValidationErrorCode.DUPLICATE_VALUE.getCode()));
                    ApiResponse<?> body = ApiResponse.error(errors);
                    return new ResponseEntity<>(body, HttpStatus.CONFLICT);
                }
            }
        }

        // Fallback: generic DB integrity error
        SystemErrorCode code = SystemErrorCode.DATABASE_INTEGRITY_ERROR;
        errors.add(new ApiError(null, code.getMessage(), code.getCode()));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    // Map DB constraint/column to API field name when possible
    private String mapDbColumnToField(String constraintOrColumn) {
        String s = constraintOrColumn.toLowerCase();
        if (s.contains("benefit_number") || s.contains("benefitnumber")) return "benefitNumber";
        if (s.contains("cpf")) return "cpf";
        if (s.contains("email")) return "email";
        if (s.contains("mobile_phone") || s.contains("mobilephone") || s.contains("mobile")) return "mobilePhone";
    if (s.contains("reference_phone") || s.contains("referencephone") || s.contains("reference_phone")) return "referencePhone";
    if (s.matches(".*\brg\b.*")) return "rg";
    if (s.matches(".*\\bctps\\b.*")) return "ctps";
        if (s.contains("nit_pis") || s.contains("nitpis") || s.contains("nit")) return "nitPis";
        return null;
    }

    @ExceptionHandler({ NullPointerException.class })
    protected ResponseEntity<Object> handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException", ex);
        SystemErrorCode code = SystemErrorCode.NULL_POINTER_ERROR;
        List<ApiError> errors = List.of(new ApiError(null, code.getMessage(), code.getCode()));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    protected ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("IllegalArgumentException", ex);
        SystemErrorCode code = SystemErrorCode.ILLEGAL_ARGUMENT_ERROR;
        List<ApiError> errors = List.of(new ApiError(null, code.getMessage(), code.getCode()));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        // Try to detect enum conversion errors caused by our @JsonCreator in Situation
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof IllegalArgumentException) {
            String msg = cause.getMessage();
            if (msg != null && msg.toLowerCase().contains("unknown situation")) {
                // Return a validation-style error specific for invalid situation
                List<ApiError> errors = List.of(new ApiError("situation", ValidationErrorCode.INVALID_SITUATION.getMessage(), ValidationErrorCode.INVALID_SITUATION.getCode()));
                ApiResponse<?> body = ApiResponse.error(errors);
                return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
            }
        }

        // Fallback: malformed JSON or other deserialization issue
        SystemErrorCode code = SystemErrorCode.CONVERSION_ERROR;
        List<ApiError> errors = List.of(new ApiError(null, code.getMessage(), code.getCode()));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ ClassCastException.class })
    protected ResponseEntity<Object> handleConversion(ClassCastException ex) {
        log.error("Conversion error", ex);
        SystemErrorCode code = SystemErrorCode.CONVERSION_ERROR;
        List<ApiError> errors = List.of(new ApiError(null, code.getMessage(), code.getCode()));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        SystemErrorCode code = SystemErrorCode.SYSTEM_ERROR;
        List<ApiError> errors = List.of(new ApiError(null, code.getMessage(), code.getCode()));
        ApiResponse<?> body = ApiResponse.error(errors);
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
