package com.lawfirm.law.firm.exception;

import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.storage.FileStorageException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central única de tratamento de erros. Divisão em três famílias:
 *
 * <p>1. VALIDAÇÃO (400) - requisição malformada ou campo inválido. Não é logada como problema (é
 * comportamento normal da API). 2. NEGÓCIO (404/409/422) - requisição válida, mas o domínio não
 * permite: recurso inexistente, duplicidade, regra violada. Logada como WARN. 3. SISTEMA (500) -
 * falha de infraestrutura ou bug. Logada como ERROR com stack trace; a resposta NUNCA expõe detalhe
 * técnico, só o código.
 *
 * <p>Toda resposta de erro usa o envelope padrão {@link ApiResponse} com a lista de {@link
 * ApiError} (field, message, code).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Mapeia colunas do banco para nomes de campo da API nas violações de unicidade. */
    private static final Map<String, String> COLUMN_TO_FIELD =
            Map.of(
                    "cpf", "cpf",
                    "email", "email",
                    "benefit_number", "beneficiaryNumber",
                    "nit_pis", "nitPis");

    /** Labels PT-BR para mensagens de validação amigáveis. */
    private static final Map<String, String> FIELD_LABELS =
            Map.ofEntries(
                    Map.entry("fullName", "Nome completo"),
                    Map.entry("birthDate", "Data de nascimento"),
                    Map.entry("cpf", "CPF"),
                    Map.entry("motherName", "Nome da mãe"),
                    Map.entry("mobilePhone", "Telefone celular"),
                    Map.entry("inssPassword", "Senha do INSS"),
                    Map.entry("gender", "Gênero"),
                    Map.entry("situation", "Situação"),
                    Map.entry("benefit", "Benefício"),
                    Map.entry("email", "E-mail"),
                    Map.entry("documentType", "Tipo do documento"),
                    Map.entry("content", "Conteúdo"),
                    Map.entry("notBillable", "Arrecadação"),
                    Map.entry("amount", "Valor"),
                    Map.entry("dueDate", "Vencimento"),
                    Map.entry("description", "Descrição"),
                    Map.entry("street", "Rua"),
                    Map.entry("city", "Cidade"),
                    Map.entry("state", "UF"));

    /** Extrai a coluna de mensagens Postgres "Key (column)=(value)". */
    private static final Pattern KEY_PATTERN = Pattern.compile("Key \\(([^)]+)\\)");

    // ═══════════════ 1. VALIDAÇÃO (400) ═══════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBeanValidation(
            MethodArgumentNotValidException ex) {
        List<ApiError> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fe -> {
                                    String label =
                                            FIELD_LABELS.getOrDefault(fe.getField(), fe.getField());
                                    return new ApiError(
                                            fe.getField(),
                                            humanize(label, fe.getDefaultMessage()),
                                            "VALIDATION_ERROR");
                                })
                        .toList();
        return respond(HttpStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<ApiError> errors =
                ex.getConstraintViolations().stream()
                        .map(
                                cv -> {
                                    String path = cv.getPropertyPath().toString();
                                    String field =
                                            path.contains(".")
                                                    ? path.substring(path.lastIndexOf('.') + 1)
                                                    : path;
                                    String label = FIELD_LABELS.getOrDefault(field, field);
                                    return new ApiError(
                                            field,
                                            humanize(label, cv.getMessage()),
                                            "VALIDATION_ERROR");
                                })
                        .toList();
        return respond(HttpStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomValidation(ValidationException ex) {
        String code =
                ex.getValidationErrorCode() != null
                        ? ex.getValidationErrorCode().getCode()
                        : ex.getCode();
        return respond(
                HttpStatus.BAD_REQUEST,
                List.of(new ApiError(ex.getField(), ex.getMessage(), code)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String message = "Valor inválido para o parâmetro '" + ex.getName() + "': " + ex.getValue();
        return respond(
                HttpStatus.BAD_REQUEST,
                List.of(new ApiError(ex.getName(), message, "INVALID_PARAMETER")));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception ex) {
        String name =
                ex instanceof MissingServletRequestParameterException p
                        ? p.getParameterName()
                        : ((MissingServletRequestPartException) ex).getRequestPartName();
        return respond(
                HttpStatus.BAD_REQUEST,
                List.of(
                        new ApiError(
                                name, "Parâmetro obrigatório ausente: " + name, "REQUIRED_FIELD")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        String msg =
                ex.getMostSpecificCause() != null
                        ? ex.getMostSpecificCause().getMessage()
                        : ex.getMessage();
        return respond(
                HttpStatus.BAD_REQUEST, List.of(new ApiError(null, msg, "INVALID_REQUEST_BODY")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex) {
        return respond(
                HttpStatus.BAD_REQUEST,
                List.of(
                        new ApiError(
                                "files",
                                "Arquivo(s) excedem o tamanho máximo permitido para upload.",
                                "FILE_TOO_LARGE")));
    }

    // ═══════════════ 2. NEGÓCIO (404/409/422) ═══════════════

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return respond(
                HttpStatus.NOT_FOUND, List.of(new ApiError(null, ex.getMessage(), "NOT_FOUND")));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Regra de negócio violada [{}]: {}", ex.getCode(), ex.getMessage());
        return respond(
                HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError(null, ex.getMessage(), ex.getCode())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        String detail = rootMessage(ex);
        Matcher m = KEY_PATTERN.matcher(detail);
        if (m.find()) {
            String column = m.group(1).trim();
            String field = COLUMN_TO_FIELD.getOrDefault(column, column);
            log.warn("Violação de unicidade na coluna '{}'", column);
            return respond(
                    HttpStatus.CONFLICT,
                    List.of(
                            new ApiError(
                                    field, "Valor duplicado para campo único", "DUPLICATE_VALUE")));
        }
        log.error("Violação de integridade não mapeada", ex);
        return respond(
                HttpStatus.CONFLICT,
                List.of(
                        new ApiError(
                                null,
                                SystemErrorCode.DATABASE_INTEGRITY_ERROR.getMessage(),
                                SystemErrorCode.DATABASE_INTEGRITY_ERROR.getCode())));
    }

    // ═══════════════ Autenticação / autorização ═══════════════

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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(
                HttpStatus.NOT_FOUND,
                List.of(
                        new ApiError(
                                null,
                                "Rota não encontrada: " + ex.getResourcePath(),
                                "NOT_FOUND")));
    }

    // ═══════════════ 3. SISTEMA (500) ═══════════════

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystem(SystemException ex) {
        log.error("Erro de sistema [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        String message =
                ex.getErrorCode() != null
                        ? ex.getErrorCode().getMessage()
                        : SystemErrorCode.SYSTEM_ERROR.getMessage();
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(new ApiError(null, message, ex.getCode())));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorage(FileStorageException ex) {
        log.error("Falha no storage de arquivos", ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(
                        new ApiError(
                                null,
                                SystemErrorCode.FILE_STORAGE_ERROR.getMessage(),
                                SystemErrorCode.FILE_STORAGE_ERROR.getCode())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Erro não tratado", ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(
                        new ApiError(
                                null,
                                SystemErrorCode.SYSTEM_ERROR.getMessage(),
                                SystemErrorCode.SYSTEM_ERROR.getCode())));
    }

    // ── Helpers ──

    private static ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, List<ApiError> errors) {
        return ResponseEntity.status(status).body(ApiResponse.error(errors));
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : "";
    }

    private static String humanize(String label, String defaultMsg) {
        if (defaultMsg == null) return "Por favor, verifique o campo: " + label + ".";
        String msg = defaultMsg.toLowerCase();
        if (msg.contains("not be blank")
                || msg.contains("not be null")
                || msg.contains("não pode estar em branco")
                || msg.contains("não pode ser nulo")) {
            return "Por favor, informe " + label + ".";
        }
        if (msg.contains("invalid") || msg.contains("inválido")) {
            return "Valor inválido para " + label + ".";
        }
        return defaultMsg.substring(0, 1).toUpperCase() + defaultMsg.substring(1);
    }
}
