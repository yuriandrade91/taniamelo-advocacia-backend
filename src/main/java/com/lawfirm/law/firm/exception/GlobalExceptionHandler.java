package com.lawfirm.law.firm.exception;

import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.storage.FileStorageException;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
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
import tools.jackson.core.JacksonException;

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

        // Enum fields deserialized via a @JsonCreator that throws (every domain enum - ClientType,
        // Situation, BenefitType, Gender, etc.) surface here as a Jackson 3 JacksonException
        // (Jackson
        // 3 dropped the old com.fasterxml.jackson JsonMappingException type) whose path tells us
        // which JSON field actually failed, instead of a bare, field-less 400.
        String field = null;
        if (ex.getCause() instanceof JacksonException jacksonEx && !jacksonEx.getPath().isEmpty()) {
            field = jacksonEx.getPath().get(jacksonEx.getPath().size() - 1).getPropertyName();
        }
        String code = field != null ? "INVALID_ENUM_VALUE" : "INVALID_REQUEST_BODY";
        return respond(HttpStatus.BAD_REQUEST, List.of(new ApiError(field, msg, code)));
    }

    /**
     * Validação de parâmetro de método - {@code @Valid} em {@code @RequestPart} e
     * {@code @RequestParam}.
     *
     * <p>Sem este handler a exceção caía no 500 genérico, apesar de ela própria carregar {@code 400
     * BAD_REQUEST "Validation failure"}. Na prática, subir um documento sem o {@code documentType}
     * devolvia "Erro interno do sistema. Tente novamente; se persistir, contate o suporte." - a API
     * culpando a si mesma por um campo que faltou no corpo.
     *
     * <p>Percorre {@code getParameterValidationResults()} em vez de implementar o {@code Visitor}:
     * o visitor tem um método por origem de parâmetro e obrigaria a repetir o mesmo tratamento em
     * todos, para distinguir casos que aqui não mudam nada.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException ex) {
        List<ApiError> errors = new ArrayList<>();

        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors parametroComCampos) {
                for (FieldError fe : parametroComCampos.getFieldErrors()) {
                    errors.add(erroDeCampo(nomeDoCampo(fe.getField()), fe.getDefaultMessage()));
                }
                continue;
            }
            String campo = resultado.getMethodParameter().getParameterName();
            for (MessageSourceResolvable erro : resultado.getResolvableErrors()) {
                errors.add(erroDeCampo(campo, erro.getDefaultMessage()));
            }
        }

        if (errors.isEmpty()) {
            errors.add(new ApiError(null, "Requisição inválida.", "VALIDATION_ERROR"));
        }
        return respond(HttpStatus.BAD_REQUEST, errors);
    }

    private static ApiError erroDeCampo(String campo, String mensagem) {
        String label = FIELD_LABELS.getOrDefault(campo, campo);
        return new ApiError(campo, humanize(label, mensagem), "VALIDATION_ERROR");
    }

    /**
     * {@code metadata[0].documentType} -> {@code documentType}.
     *
     * <p>O caminho completo cita o índice do item e o nome do parâmetro do controller; quem consome
     * a API precisa do nome do campo que faltou, não da assinatura do método que o recebeu.
     */
    private static String nomeDoCampo(String caminho) {
        if (caminho == null) {
            return null;
        }
        int ponto = caminho.lastIndexOf('.');
        return ponto >= 0 ? caminho.substring(ponto + 1) : caminho;
    }

    /**
     * Content-Type que a rota não aceita.
     *
     * <p>É 415, não 500: quem mandou JSON numa rota multipart cometeu um erro de cliente, e o
     * status precisa dizer isso. Caía no 500 genérico e mandava "contate o suporte" para quem só
     * precisava trocar o cabeçalho.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(
            HttpMediaTypeNotSupportedException ex) {
        return respond(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                List.of(
                        new ApiError(
                                null,
                                "Formato de requisição não suportado por esta rota.",
                                "UNSUPPORTED_MEDIA_TYPE")));
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

    /**
     * Tentativas demais de login.
     *
     * <p>429 com {@code Retry-After}: o cabeçalho é o que permite a uma tela dizer "tente em 3
     * minutos" em vez de deixar a pessoa insistindo. O corpo NÃO distingue limite por IP de conta
     * bloqueada - a diferença revelaria quais logins existem.
     */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyAttempts(TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(
                        ApiResponse.error(
                                List.of(new ApiError(null, ex.getMessage(), "TOO_MANY_ATTEMPTS"))));
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
