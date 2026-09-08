package com.lawfirm.law.firm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Exceções de domínio e catálogos de códigos de erro")
class DomainExceptionsTest {

    @Nested
    @DisplayName("NotFoundException")
    class NotFound {

        @Test
        void ofBuildsHumanReadableMessage() {
            UUID id = UUID.randomUUID();
            NotFoundException ex = NotFoundException.of("Cliente", id);
            assertTrue(ex.getMessage().startsWith("Cliente não encontrado(a) com id: "));
            assertTrue(ex.getMessage().contains(id.toString()));
        }

        @Test
        void keepsExplicitMessage() {
            assertEquals("sumiu", new NotFoundException("sumiu").getMessage());
        }
    }

    @Nested
    @DisplayName("BusinessException")
    class Business {

        @Test
        void defaultsMessageToTheErrorCodeMessage() {
            BusinessException ex = new BusinessException(BusinessErrorCode.OPERATION_NOT_ALLOWED);
            assertEquals(BusinessErrorCode.OPERATION_NOT_ALLOWED.getMessage(), ex.getMessage());
            assertEquals("OPERATION_NOT_ALLOWED", ex.getCode());
            assertSame(BusinessErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());
        }

        @Test
        void explicitMessageWinsOverTheCodeMessage() {
            BusinessException ex =
                    new BusinessException(BusinessErrorCode.BUSINESS_RULE_VIOLATION, "detalhe");
            assertEquals("detalhe", ex.getMessage());
        }

        @Test
        void nullMessageFallsBackToTheCodeMessage() {
            BusinessException ex =
                    new BusinessException(BusinessErrorCode.BUSINESS_RULE_VIOLATION, null);
            assertEquals(BusinessErrorCode.BUSINESS_RULE_VIOLATION.getMessage(), ex.getMessage());
        }

        @Test
        void nullErrorCodeFallsBackToGenericCode() {
            BusinessException ex = new BusinessException(null);
            assertNull(ex.getMessage());
            assertEquals("BUSINESS_RULE", ex.getCode());
        }
    }

    @Nested
    @DisplayName("SystemException")
    class System {

        @Test
        void carriesSafeMessageFromTheCode() {
            RuntimeException cause = new RuntimeException("stack interno");
            SystemException ex = new SystemException(SystemErrorCode.FILE_STORAGE_ERROR, cause);
            assertEquals(SystemErrorCode.FILE_STORAGE_ERROR.getMessage(), ex.getMessage());
            assertEquals("FILE_STORAGE_ERROR", ex.getCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        void internalDetailIsKeptOnlyInTheException() {
            SystemException ex =
                    new SystemException(
                            SystemErrorCode.SYSTEM_ERROR,
                            "detalhe técnico",
                            new RuntimeException());
            assertEquals("detalhe técnico", ex.getMessage());
        }

        @Test
        void nullErrorCodeFallsBackToGenericCode() {
            SystemException ex = new SystemException(null, new RuntimeException());
            assertEquals("SYSTEM_ERROR", ex.getCode());
            assertNull(ex.getMessage());
        }
    }

    @Nested
    @DisplayName("ValidationException")
    class Validation {

        @Test
        void carriesFieldAndCode() {
            ValidationException ex =
                    new ValidationException("cpf", ValidationErrorCode.INVALID_CPF);
            assertEquals("cpf", ex.getField());
            assertEquals("VALIDATION_ERROR", ex.getCode());
            assertEquals(ValidationErrorCode.INVALID_CPF, ex.getValidationErrorCode());
            assertEquals(ValidationErrorCode.INVALID_CPF.getMessage(), ex.getMessage());
        }

        @Test
        void explicitMessageWins() {
            ValidationException ex =
                    new ValidationException("cpf", ValidationErrorCode.INVALID_CPF, "CPF 123 ruim");
            assertEquals("CPF 123 ruim", ex.getMessage());
        }

        @Test
        void nullMessageFallsBackToCodeMessage() {
            ValidationException ex =
                    new ValidationException("cpf", ValidationErrorCode.INVALID_CPF, null);
            assertEquals(ValidationErrorCode.INVALID_CPF.getMessage(), ex.getMessage());
        }

        @Test
        void supportsACause() {
            RuntimeException cause = new RuntimeException("raiz");
            ValidationException ex =
                    new ValidationException(
                            "email", ValidationErrorCode.INVALID_EMAIL, "ruim", cause);
            assertSame(cause, ex.getCause());
            assertEquals("ruim", ex.getMessage());
        }

        @Test
        void nullCodeAndNullMessageLeaveMessageNull() {
            assertNull(new ValidationException("x", null, null).getMessage());
            assertNull(new ValidationException("x", null).getMessage());
        }
    }

    @Nested
    @DisplayName("Catálogos de códigos")
    class Codes {

        @ParameterizedTest
        @EnumSource(ValidationErrorCode.class)
        void validationCodesExposeNameAndMessage(ValidationErrorCode code) {
            assertEquals(code.name(), code.getCode());
            assertNotNull(code.getMessage());
            assertEquals(code.name() + ": " + code.getMessage(), code.toString());
        }

        @ParameterizedTest
        @EnumSource(BusinessErrorCode.class)
        void businessCodesExposeNameAndMessage(BusinessErrorCode code) {
            assertEquals(code.name(), code.getCode());
            assertNotNull(code.getMessage());
            assertEquals(code.name() + ": " + code.getMessage(), code.toString());
        }

        @ParameterizedTest
        @EnumSource(SystemErrorCode.class)
        void systemCodesExposeNameAndMessage(SystemErrorCode code) {
            assertEquals(code.name(), code.getCode());
            assertNotNull(code.getMessage());
            assertEquals(code.name() + ": " + code.getMessage(), code.toString());
        }
    }
}
