package com.lawfirm.law.firm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.storage.FileStorageException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("GlobalExceptionHandler: central única de tratamento de erros")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    private static List<ApiError> errorsOf(ResponseEntity<ApiResponse<Void>> response) {
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body, "corpo do envelope");
        assertFalse(body.isSuccess(), "toda resposta de erro tem success=false");
        assertEquals(List.of(), body.getData(), "data vem como lista vazia nos erros");
        assertNotNull(body.getErrors());
        return body.getErrors();
    }

    @Nested
    @DisplayName("1. Validação (400)")
    class ValidationFamily {

        /**
         * {@code @Valid} em {@code @RequestPart} — o caso dos uploads de arquivo.
         *
         * <p>Sem handler, {@code HandlerMethodValidationException} caía no 500 genérico, apesar de
         * ela própria carregar {@code 400 BAD_REQUEST}. Subir um documento sem {@code documentType}
         * devolvia "Erro interno do sistema... contate o suporte" — a API culpando a si mesma por
         * um campo que faltou no corpo.
         */
        @Test
        @DisplayName("validação de @RequestPart vira 400 com o nome do campo, não 500")
        void requestPartValidationIsBadRequest() throws Exception {
            BeanPropertyBindingResult binding =
                    new BeanPropertyBindingResult(new Object(), "metadata");
            binding.addError(
                    new FieldError(
                            "metadata", "metadata[0].documentType", "não deve estar em branco"));

            MethodParameter parametro =
                    new MethodParameter(Alvo.class.getDeclaredMethod("upload", List.class), 0);
            ParameterErrors resultado =
                    new ParameterErrors(parametro, null, binding, null, null, null);
            HandlerMethodValidationException ex =
                    new HandlerMethodValidationException(
                            MethodValidationResult.create(
                                    new Alvo(),
                                    Alvo.class.getDeclaredMethod("upload", List.class),
                                    List.of(resultado)));

            ResponseEntity<ApiResponse<Void>> resposta = handler.handleMethodValidation(ex);

            assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
            List<ApiError> erros = errorsOf(resposta);
            // O caminho completo é metadata[0].documentType; quem consome precisa do
            // nome do campo, não da assinatura do método do controller.
            assertEquals("documentType", erros.get(0).getField());
            assertEquals("VALIDATION_ERROR", erros.get(0).getCode());
        }

        @Test
        @DisplayName("Content-Type não suportado vira 415, não 500")
        void unsupportedMediaTypeIs415() {
            // Quem mandou JSON numa rota multipart cometeu erro de cliente. Caindo no
            // 500 genérico, a API mandava "contate o suporte" para quem só precisava
            // trocar o cabeçalho.
            ResponseEntity<ApiResponse<Void>> resposta =
                    handler.handleMediaType(
                            new HttpMediaTypeNotSupportedException(
                                    org.springframework.http.MediaType.APPLICATION_JSON,
                                    List.of(
                                            org.springframework.http.MediaType
                                                    .MULTIPART_FORM_DATA)));

            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, resposta.getStatusCode());
            assertEquals("UNSUPPORTED_MEDIA_TYPE", errorsOf(resposta).get(0).getCode());
        }

        /** Alvo mínimo só para obter um MethodParameter real. */
        static class Alvo {
            @SuppressWarnings("unused")
            void upload(List<String> metadata) {}
        }

        @Test
        @DisplayName("bean validation humaniza a mensagem usando o label PT-BR do campo")
        void beanValidationIsHumanized() throws Exception {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "dto");
            binding.rejectValue(null, "x");
            BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "dto");
            result.addError(
                    new org.springframework.validation.FieldError(
                            "dto", "fullName", "must not be blank"));
            result.addError(
                    new org.springframework.validation.FieldError(
                            "dto", "email", "must be a well-formed email address"));

            MethodParameter parameter =
                    new MethodParameter(
                            GlobalExceptionHandlerTest.class.getDeclaredMethod(
                                    "dummy", String.class),
                            0);
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleBeanValidation(
                            new MethodArgumentNotValidException(parameter, result));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            List<ApiError> errors = errorsOf(response);
            assertEquals(2, errors.size());
            assertEquals("fullName", errors.get(0).getField());
            assertEquals("Por favor, informe Nome completo.", errors.get(0).getMessage());
            assertEquals("VALIDATION_ERROR", errors.get(0).getCode());
            assertEquals("Must be a well-formed email address", errors.get(1).getMessage());
            assertNotNull(binding);
        }

        @Test
        @DisplayName("campo sem label conhecido usa o próprio nome do campo")
        void unknownFieldKeepsItsName() throws Exception {
            BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "dto");
            result.addError(
                    new org.springframework.validation.FieldError(
                            "dto", "campoExotico", "não pode ser nulo"));
            MethodParameter parameter =
                    new MethodParameter(
                            GlobalExceptionHandlerTest.class.getDeclaredMethod(
                                    "dummy", String.class),
                            0);

            List<ApiError> errors =
                    errorsOf(
                            handler.handleBeanValidation(
                                    new MethodArgumentNotValidException(parameter, result)));
            assertEquals("Por favor, informe campoExotico.", errors.get(0).getMessage());
        }

        @Test
        @DisplayName("mensagem nula vira o texto genérico de verificação do campo")
        void nullDefaultMessageFallsBack() throws Exception {
            BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "dto");
            result.addError(
                    new org.springframework.validation.FieldError(
                            "dto", "cpf", null, false, null, null, null));
            MethodParameter parameter =
                    new MethodParameter(
                            GlobalExceptionHandlerTest.class.getDeclaredMethod(
                                    "dummy", String.class),
                            0);

            List<ApiError> errors =
                    errorsOf(
                            handler.handleBeanValidation(
                                    new MethodArgumentNotValidException(parameter, result)));
            assertEquals("Por favor, verifique o campo: CPF.", errors.get(0).getMessage());
        }

        @Test
        @DisplayName("ConstraintViolation usa só o último segmento do property path")
        void constraintViolationUsesLeafField() {
            ConstraintViolation<?> violation = mock(ConstraintViolation.class);
            Path path = mock(Path.class);
            when(path.toString()).thenReturn("create.dto.amount");
            when(violation.getPropertyPath()).thenReturn(path);
            when(violation.getMessage()).thenReturn("must not be null");

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleConstraint(new ConstraintViolationException(Set.of(violation)));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            List<ApiError> errors = errorsOf(response);
            assertEquals("amount", errors.get(0).getField());
            assertEquals("Por favor, informe Valor.", errors.get(0).getMessage());
        }

        @Test
        @DisplayName("ConstraintViolation sem ponto no path mantém o path inteiro")
        void constraintViolationWithoutDots() {
            ConstraintViolation<?> violation = mock(ConstraintViolation.class);
            Path path = mock(Path.class);
            when(path.toString()).thenReturn("cpf");
            when(violation.getPropertyPath()).thenReturn(path);
            when(violation.getMessage()).thenReturn("inválido");

            List<ApiError> errors =
                    errorsOf(
                            handler.handleConstraint(
                                    new ConstraintViolationException(Set.of(violation))));
            assertEquals("cpf", errors.get(0).getField());
            assertEquals("Valor inválido para CPF.", errors.get(0).getMessage());
        }

        @Test
        @DisplayName("ValidationException do domínio preserva campo e código")
        void customValidationKeepsFieldAndCode() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleCustomValidation(
                            new ValidationException(
                                    "situation",
                                    ValidationErrorCode.INVALID_SITUATION,
                                    "Situação inválida: xpto"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("situation", error.getField());
            assertEquals("INVALID_SITUATION", error.getCode());
            assertEquals("Situação inválida: xpto", error.getMessage());
        }

        @Test
        @DisplayName("ValidationException sem código específico cai no VALIDATION_ERROR genérico")
        void customValidationWithoutCode() {
            ApiError error =
                    errorsOf(
                                    handler.handleCustomValidation(
                                            new ValidationException("x", null, "ruim")))
                            .get(0);
            assertEquals("VALIDATION_ERROR", error.getCode());
        }

        @Test
        @DisplayName("tipo errado em query param vira INVALID_PARAMETER")
        void typeMismatch() {
            MethodArgumentTypeMismatchException ex =
                    new MethodArgumentTypeMismatchException(
                            "abc", Integer.class, "pageNumber", null, new NumberFormatException());

            ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("pageNumber", error.getField());
            assertEquals("INVALID_PARAMETER", error.getCode());
            assertTrue(error.getMessage().contains("abc"));
        }

        @Test
        @DisplayName("parâmetro obrigatório ausente vira REQUIRED_FIELD")
        void missingRequestParameter() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMissingPart(
                            new MissingServletRequestParameterException("year", "int"));
            ApiError error = errorsOf(response).get(0);
            assertEquals("year", error.getField());
            assertEquals("REQUIRED_FIELD", error.getCode());
            assertEquals("Parâmetro obrigatório ausente: year", error.getMessage());
        }

        @Test
        @DisplayName("parte multipart obrigatória ausente vira REQUIRED_FIELD")
        void missingRequestPart() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMissingPart(new MissingServletRequestPartException("metadata"));
            ApiError error = errorsOf(response).get(0);
            assertEquals("metadata", error.getField());
            assertEquals("REQUIRED_FIELD", error.getCode());
        }

        @Test
        @DisplayName("JSON ilegível sem causa Jackson vira INVALID_REQUEST_BODY sem campo")
        void unreadableBodyWithoutJacksonPath() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnreadable(
                            new HttpMessageNotReadableException(
                                    "corpo inválido",
                                    (org.springframework.http.HttpInputMessage) null));
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertNull(error.getField());
            assertEquals("INVALID_REQUEST_BODY", error.getCode());
        }

        @Test
        @DisplayName("upload acima do limite vira FILE_TOO_LARGE no campo files")
        void maxUploadSize() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleMaxUploadSize(new MaxUploadSizeExceededException(10L));
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("files", error.getField());
            assertEquals("FILE_TOO_LARGE", error.getCode());
        }
    }

    @Nested
    @DisplayName("2. Negócio (404/409/422)")
    class BusinessFamily {

        @Test
        @DisplayName("NotFoundException vira 404 NOT_FOUND")
        void notFound() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleNotFound(new NotFoundException("Cliente não encontrado"));
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertNull(error.getField());
            assertEquals("NOT_FOUND", error.getCode());
            assertEquals("Cliente não encontrado", error.getMessage());
        }

        @Test
        @DisplayName("BusinessException vira 422 com o código do domínio")
        void businessRule() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleBusiness(
                            new BusinessException(BusinessErrorCode.PAST_DATE_NOT_CONFIRMED));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("PAST_DATE_NOT_CONFIRMED", error.getCode());
        }

        @Test
        @DisplayName("violação de unicidade mapeia a coluna do Postgres para o campo da API")
        void uniqueViolationMapsColumnToApiField() {
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException(
                            "erro",
                            new RuntimeException(
                                    "ERROR: duplicate key value violates unique constraint"
                                            + " \"clients_cpf_key\"\n  Detail: Key (cpf)=(123) already exists."));

            ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(ex);
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("cpf", error.getField());
            assertEquals("DUPLICATE_VALUE", error.getCode());
            assertEquals("Valor duplicado para campo único", error.getMessage());
        }

        @Test
        @DisplayName("colunas com nome diferente do campo são traduzidas (benefit_number)")
        void uniqueViolationTranslatesColumnNames() {
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException(
                            "erro",
                            new RuntimeException(
                                    "Detail: Key (benefit_number)=(BN1) already exists."));
            assertEquals(
                    "beneficiaryNumber",
                    errorsOf(handler.handleDataIntegrity(ex)).get(0).getField());

            DataIntegrityViolationException nit =
                    new DataIntegrityViolationException(
                            "erro",
                            new RuntimeException("Detail: Key (nit_pis)=(1) already exists."));
            assertEquals("nitPis", errorsOf(handler.handleDataIntegrity(nit)).get(0).getField());
        }

        @Test
        @DisplayName("coluna sem tradução conhecida é devolvida como veio")
        void uniqueViolationKeepsUnknownColumn() {
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException(
                            "erro",
                            new RuntimeException("Detail: Key (some_column)=(v) already exists."));
            assertEquals(
                    "some_column", errorsOf(handler.handleDataIntegrity(ex)).get(0).getField());
        }

        @Test
        @DisplayName("integridade não mapeada vira 409 genérico, sem vazar SQL")
        void unmappedIntegrityViolation() {
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException(
                            "erro", new RuntimeException("violates foreign key constraint"));

            ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(ex);
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertNull(error.getField());
            assertEquals("DATABASE_INTEGRITY_ERROR", error.getCode());
            assertEquals(-1, error.getMessage().indexOf("foreign key"));
        }

        @Test
        @DisplayName("causa raiz sem mensagem não quebra o handler")
        void integrityViolationWithoutRootMessage() {
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException(
                            "erro", new RuntimeException((String) null));
            assertEquals(HttpStatus.CONFLICT, handler.handleDataIntegrity(ex).getStatusCode());
        }
    }

    @Nested
    @DisplayName("Autenticação e autorização")
    class AuthFamily {

        @Test
        @DisplayName("falha de autenticação vira 401 com mensagem neutra")
        void authenticationFailure() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAuthentication(new BadCredentialsException("senha do usuário X"));
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("INVALID_CREDENTIALS", error.getCode());
            assertEquals("Credenciais inválidas", error.getMessage());
        }

        @Test
        @DisplayName("acesso negado vira 403")
        void accessDenied() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAccessDenied(new AccessDeniedException("nope"));
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("ACCESS_DENIED", errorsOf(response).get(0).getCode());
        }

        @Test
        @DisplayName("rota inexistente vira 404 informando o caminho")
        void noResourceFound() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleNoResource(
                            new NoResourceFoundException(
                                    org.springframework.http.HttpMethod.GET,
                                    "/api/v1/nao-existe",
                                    "/api/v1/nao-existe"));
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertTrue(errorsOf(response).get(0).getMessage().contains("nao-existe"));
        }
    }

    @Nested
    @DisplayName("3. Sistema (500) - nunca vaza detalhe técnico")
    class SystemFamily {

        @Test
        @DisplayName("SystemException devolve só a mensagem segura do código")
        void systemException() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleSystem(
                            new SystemException(
                                    SystemErrorCode.DATABASE_INTEGRITY_ERROR,
                                    "connection refused em 10.0.0.5:5432",
                                    new RuntimeException()));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("DATABASE_INTEGRITY_ERROR", error.getCode());
            assertEquals(SystemErrorCode.DATABASE_INTEGRITY_ERROR.getMessage(), error.getMessage());
            assertEquals(-1, error.getMessage().indexOf("10.0.0.5"));
        }

        @Test
        @DisplayName("SystemException sem código usa a mensagem genérica")
        void systemExceptionWithoutCode() {
            ApiError error =
                    errorsOf(
                                    handler.handleSystem(
                                            new SystemException(null, new RuntimeException())))
                            .get(0);
            assertEquals(SystemErrorCode.SYSTEM_ERROR.getMessage(), error.getMessage());
            assertEquals("SYSTEM_ERROR", error.getCode());
        }

        @Test
        @DisplayName("falha de storage vira 500 FILE_STORAGE_ERROR sem expor o caminho do arquivo")
        void fileStorageFailure() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleFileStorage(
                            new FileStorageException("/var/data/storage/clients/1/x.pdf sumiu"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("FILE_STORAGE_ERROR", error.getCode());
            assertEquals(-1, error.getMessage().indexOf("/var/data"));
        }

        @Test
        @DisplayName("qualquer exceção não tratada vira 500 genérico")
        void genericFallback() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneric(
                            new IllegalStateException("NullPointer em FooService:42"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            ApiError error = errorsOf(response).get(0);
            assertEquals("SYSTEM_ERROR", error.getCode());
            assertEquals(SystemErrorCode.SYSTEM_ERROR.getMessage(), error.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
        // alvo para construir um MethodParameter real nos testes de bean validation
    }
}
