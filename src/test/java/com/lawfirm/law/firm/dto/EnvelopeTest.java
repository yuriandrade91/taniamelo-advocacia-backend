package com.lawfirm.law.firm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("Envelope padrão da API: ApiResponse, ApiError e Pagination")
class EnvelopeTest {

    @Nested
    @DisplayName("ApiResponse")
    class Envelope {

        @Test
        @DisplayName("successObject marca sucesso e carrega o objeto")
        void successObject() {
            ApiResponse<String> response = ApiResponse.successObject("valor");

            assertTrue(response.isSuccess());
            assertEquals("valor", response.getData());
            assertNull(response.getPagination());
            assertNull(response.getErrors());
        }

        @Test
        @DisplayName("successObject com paginação carrega os dois")
        void successObjectWithPagination() {
            Pagination pagination = new Pagination(1, 10, 5);
            ApiResponse<String> response = ApiResponse.successObject("valor", pagination);

            assertTrue(response.isSuccess());
            assertSame(pagination, response.getPagination());
        }

        @Test
        @DisplayName("successList carrega a lista e a paginação")
        void successList() {
            Pagination pagination = new Pagination(2, 10, 25);
            ApiResponse<String> response = ApiResponse.successList(List.of("a", "b"), pagination);

            assertTrue(response.isSuccess());
            assertEquals(List.of("a", "b"), response.getData());
            assertSame(pagination, response.getPagination());
        }

        @Test
        @DisplayName("successList sem paginação deixa o bloco nulo")
        void successListWithoutPagination() {
            assertNull(ApiResponse.successList(List.of("a")).getPagination());
        }

        @Test
        @DisplayName("error marca falha, zera data como lista vazia e carrega os erros")
        void error() {
            List<ApiError> errors = List.of(new ApiError("cpf", "inválido", "VALIDATION_ERROR"));
            ApiResponse<Void> response = ApiResponse.error(errors);

            assertFalse(response.isSuccess());
            assertEquals(List.of(), response.getData());
            assertEquals(errors, response.getErrors());
        }

        @Test
        @DisplayName("os setters permitem desserializar o envelope de volta")
        void settersSupportDeserialization() {
            ApiResponse<String> response = new ApiResponse<>();
            Pagination pagination = new Pagination(1, 10, 1);
            List<ApiError> errors = List.of(new ApiError(null, "erro", "X"));

            response.setSuccess(true);
            response.setData("x");
            response.setPagination(pagination);
            response.setErrors(errors);

            assertTrue(response.isSuccess());
            assertEquals("x", response.getData());
            assertSame(pagination, response.getPagination());
            assertSame(errors, response.getErrors());
        }
    }

    @Nested
    @DisplayName("ApiError")
    class Error {

        @Test
        @DisplayName("construtor completo e getters")
        void fullConstructor() {
            ApiError error = new ApiError("cpf", "CPF inválido", "VALIDATION_ERROR");

            assertEquals("cpf", error.getField());
            assertEquals("CPF inválido", error.getMessage());
            assertEquals("VALIDATION_ERROR", error.getCode());
        }

        @Test
        @DisplayName("erros globais têm field nulo")
        void globalErrorsHaveNoField() {
            assertNull(new ApiError(null, "Erro interno", "SYSTEM_ERROR").getField());
        }

        @Test
        @DisplayName("setters permitem desserialização")
        void setters() {
            ApiError error = new ApiError();
            error.setField("email");
            error.setMessage("inválido");
            error.setCode("INVALID_EMAIL");

            assertEquals("email", error.getField());
            assertEquals("inválido", error.getMessage());
            assertEquals("INVALID_EMAIL", error.getCode());
        }
    }

    @Nested
    @DisplayName("Pagination")
    class PaginationBlock {

        @ParameterizedTest(name = "página {0} de {2} registros ({1}/página) -> {3} páginas")
        @CsvSource({
            "1, 10, 0,  0, false, false",
            "1, 10, 1,  1, false, false",
            "1, 10, 10, 1, false, false",
            "1, 10, 11, 2, true,  false",
            "2, 10, 25, 3, true,  true",
            "3, 10, 25, 3, false, true",
            "1, 5,  12, 3, true,  false"
        })
        void computesTotalPagesAndNavigationFlags(
                int pageNumber,
                int pageSize,
                long totalRecords,
                int expectedTotalPages,
                boolean expectedHasNext,
                boolean expectedHasPrevious) {
            Pagination pagination = new Pagination(pageNumber, pageSize, totalRecords);

            assertEquals(pageNumber, pagination.getPageNumber());
            assertEquals(pageSize, pagination.getPageSize());
            assertEquals(totalRecords, pagination.getTotalRecords());
            assertEquals(expectedTotalPages, pagination.getTotalPages());
            assertEquals(expectedHasNext, pagination.isHasNextPage());
            assertEquals(expectedHasPrevious, pagination.isHasPreviousPage());
        }

        @Test
        @DisplayName("tamanho de página zero não divide por zero")
        void zeroPageSizeIsSafe() {
            Pagination pagination = new Pagination(1, 0, 100);
            assertEquals(0, pagination.getTotalPages());
            assertFalse(pagination.isHasNextPage());
        }

        @Test
        @DisplayName("of() converte o Page 0-based do Spring Data para o 1-based da API")
        void ofConvertsFromSpringDataPage() {
            Page<String> page = new PageImpl<>(List.of("a"), PageRequest.of(2, 5), 26);

            Pagination pagination = Pagination.of(page);

            assertEquals(3, pagination.getPageNumber(), "página 2 (0-based) vira 3 (1-based)");
            assertEquals(5, pagination.getPageSize());
            assertEquals(26, pagination.getTotalRecords());
            assertEquals(6, pagination.getTotalPages());
            assertTrue(pagination.isHasNextPage());
            assertTrue(pagination.isHasPreviousPage());
        }

        @Test
        @DisplayName("página vazia devolve o bloco zerado")
        void emptyPage() {
            Pagination pagination =
                    Pagination.of(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            assertEquals(1, pagination.getPageNumber());
            assertEquals(0, pagination.getTotalRecords());
            assertEquals(0, pagination.getTotalPages());
            assertFalse(pagination.isHasNextPage());
            assertFalse(pagination.isHasPreviousPage());
        }

        @Test
        @DisplayName("setters permitem desserialização do bloco")
        void setters() {
            Pagination pagination = new Pagination();
            pagination.setPageNumber(2);
            pagination.setPageSize(20);
            pagination.setTotalRecords(50);
            pagination.setTotalPages(3);
            pagination.setHasNextPage(true);
            pagination.setHasPreviousPage(true);

            assertEquals(2, pagination.getPageNumber());
            assertEquals(20, pagination.getPageSize());
            assertEquals(50, pagination.getTotalRecords());
            assertEquals(3, pagination.getTotalPages());
            assertTrue(pagination.isHasNextPage());
            assertTrue(pagination.isHasPreviousPage());
        }
    }
}
