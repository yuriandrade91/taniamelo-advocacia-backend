package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseListItemDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseRequestDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseResponseDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseSearchParams;
import com.lawfirm.law.firm.dto.OfficeRevenueListItemDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueRequestDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueResponseDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueSearchParams;
import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.service.OfficeExpenseService;
import com.lawfirm.law.firm.service.OfficeRevenueService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Despesas e receitas do escritório expõem o mesmo contrato HTTP. Cada rota é exercitada uma vez
 * por controller: o que se verifica aqui é a ligação HTTP → service (status, envelope, parâmetros
 * de série), não a regra de negócio, que tem teste próprio nos services.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Financeiro do escritório: rotas de despesas e receitas")
class OfficeFinanceControllersTest {

    private static final UUID ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000009");
    private static final String CORPO =
            """
            {"description":"Lançamento","amount":150.00,"dueDate":"2026-03-10"}""";

    @Mock private OfficeExpenseService expenseService;
    @Mock private OfficeRevenueService revenueService;

    private MockMvc mvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("/api/v1/expenses")
    class Expenses {

        private MockMvc mvc;

        @BeforeEach
        void setUp() {
            mvc = mvcFor(new OfficeExpenseController(expenseService));
        }

        @Test
        @DisplayName("POST cria e responde 201 com o lançamento")
        void createReturns201() throws Exception {
            OfficeExpenseResponseDTO criada = new OfficeExpenseResponseDTO();
            criada.setId(ID);
            when(expenseService.create(any(OfficeExpenseRequestDTO.class))).thenReturn(criada);

            mvc.perform(
                            post("/api/v1/expenses")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(CORPO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(ID.toString()));
        }

        @Test
        @DisplayName("GET lista com o bloco de paginação")
        void listIsPaginated() throws Exception {
            when(expenseService.list(any(OfficeExpenseSearchParams.class)))
                    .thenReturn(
                            new PageImpl<>(
                                    List.of(new OfficeExpenseListItemDTO()),
                                    PageRequest.of(0, 10),
                                    1));

            mvc.perform(get("/api/v1/expenses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.pagination.totalRecords").value(1));
        }

        @Test
        @DisplayName("GET, PUT e DELETE por id")
        void itemRoutes() throws Exception {
            OfficeExpenseResponseDTO dto = new OfficeExpenseResponseDTO();
            dto.setId(ID);
            when(expenseService.get(ID)).thenReturn(dto);
            when(expenseService.update(eq(ID), any(OfficeExpenseRequestDTO.class))).thenReturn(dto);

            mvc.perform(get("/api/v1/expenses/" + ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(ID.toString()));
            mvc.perform(
                            put("/api/v1/expenses/" + ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(CORPO))
                    .andExpect(status().isOk());
            mvc.perform(delete("/api/v1/expenses/" + ID)).andExpect(status().isNoContent());
            verify(expenseService).delete(ID);
        }

        @Test
        @DisplayName("summary e timeline (basis=paid vira regime de caixa)")
        void aggregates() throws Exception {
            when(expenseService.summary(any(OfficeExpenseSearchParams.class)))
                    .thenReturn(new FinanceSummaryDTO());
            LocalDate de = LocalDate.of(2026, 1, 1);
            LocalDate ate = LocalDate.of(2026, 3, 31);
            when(expenseService.timeline(de, ate, true))
                    .thenReturn(List.of(new FinanceTimelinePointDTO("2026-01")));

            mvc.perform(get("/api/v1/expenses/summary")).andExpect(status().isOk());
            mvc.perform(
                            get("/api/v1/expenses/timeline")
                                    .param("from", "2026-01-01")
                                    .param("to", "2026-03-31")
                                    .param("basis", "paid"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].month").value("2026-01"));
        }

        @Test
        @DisplayName("basis desconhecido na timeline é 400")
        void unknownBasisIs400() throws Exception {
            mvc.perform(
                            get("/api/v1/expenses/timeline")
                                    .param("from", "2026-01-01")
                                    .param("to", "2026-03-31")
                                    .param("basis", "semanal"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("basis"));
        }
    }

    @Nested
    @DisplayName("/api/v1/revenues")
    class Revenues {

        private MockMvc mvc;

        @BeforeEach
        void setUp() {
            mvc = mvcFor(new OfficeRevenueController(revenueService));
        }

        @Test
        @DisplayName("POST cria e responde 201 com o lançamento")
        void createReturns201() throws Exception {
            OfficeRevenueResponseDTO criada = new OfficeRevenueResponseDTO();
            criada.setId(ID);
            when(revenueService.create(any(OfficeRevenueRequestDTO.class))).thenReturn(criada);

            mvc.perform(
                            post("/api/v1/revenues")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(CORPO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(ID.toString()));
        }

        @Test
        @DisplayName("GET lista com o bloco de paginação")
        void listIsPaginated() throws Exception {
            when(revenueService.list(any(OfficeRevenueSearchParams.class)))
                    .thenReturn(
                            new PageImpl<>(
                                    List.of(new OfficeRevenueListItemDTO()),
                                    PageRequest.of(0, 10),
                                    1));

            mvc.perform(get("/api/v1/revenues"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagination.totalRecords").value(1));
        }

        @Test
        @DisplayName("GET, PUT e DELETE por id")
        void itemRoutes() throws Exception {
            OfficeRevenueResponseDTO dto = new OfficeRevenueResponseDTO();
            dto.setId(ID);
            when(revenueService.get(ID)).thenReturn(dto);
            when(revenueService.update(eq(ID), any(OfficeRevenueRequestDTO.class))).thenReturn(dto);

            mvc.perform(get("/api/v1/revenues/" + ID)).andExpect(status().isOk());
            mvc.perform(
                            put("/api/v1/revenues/" + ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(CORPO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(ID.toString()));
            mvc.perform(delete("/api/v1/revenues/" + ID)).andExpect(status().isNoContent());
            verify(revenueService).delete(ID);
        }

        @Test
        @DisplayName("summary e timeline (sem basis é competência)")
        void aggregates() throws Exception {
            when(revenueService.summary(any(OfficeRevenueSearchParams.class)))
                    .thenReturn(new FinanceSummaryDTO());
            LocalDate de = LocalDate.of(2026, 1, 1);
            LocalDate ate = LocalDate.of(2026, 3, 31);
            when(revenueService.timeline(de, ate, false))
                    .thenReturn(List.of(new FinanceTimelinePointDTO("2026-02")));

            mvc.perform(get("/api/v1/revenues/summary")).andExpect(status().isOk());
            mvc.perform(
                            get("/api/v1/revenues/timeline")
                                    .param("from", "2026-01-01")
                                    .param("to", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].month").value("2026-02"));
        }
    }
}
