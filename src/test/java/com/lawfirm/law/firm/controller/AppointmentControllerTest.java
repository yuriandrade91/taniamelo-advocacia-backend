package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lawfirm.law.firm.dto.AppointmentHistoryDTO;
import com.lawfirm.law.firm.dto.AppointmentRequestDTO;
import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.AppointmentSearchParams;
import com.lawfirm.law.firm.dto.AppointmentSummaryDTO;
import com.lawfirm.law.firm.exception.BusinessErrorCode;
import com.lawfirm.law.firm.exception.BusinessException;
import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.service.AppointmentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppointmentController: contrato HTTP da agenda")
class AppointmentControllerTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant START = Instant.parse("2099-08-20T14:30:00Z");

    @Mock private AppointmentService service;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AppointmentController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private AppointmentResponseDTO responseDto() {
        AppointmentResponseDTO dto = new AppointmentResponseDTO();
        dto.setId(ID);
        dto.setTitle("Entrevista inicial");
        dto.setType("Entrevista");
        dto.setStatus("Agendado");
        dto.setStartAt(START);
        dto.setEndAt(START.plusSeconds(3600));
        return dto;
    }

    private AppointmentRequestDTO requestDto() {
        AppointmentRequestDTO dto = new AppointmentRequestDTO();
        dto.setTitle("Entrevista inicial");
        dto.setType(AppointmentType.ENTREVISTA);
        dto.setStartAt(START);
        dto.setEndAt(START.plusSeconds(3600));
        dto.setModality(AppointmentModality.PRESENCIAL);
        return dto;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST devolve 201 com o compromisso criado")
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(responseDto());

        mockMvc.perform(
                        post("/api/v1/appointments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(requestDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("Agendado"))
                .andExpect(jsonPath("$.data.title").value("Entrevista inicial"));
    }

    @Test
    @DisplayName("POST sem título ou tipo vira 400")
    void createWithoutRequiredFieldsReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/appointments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST com término anterior ao início vira 400 (regra @AssertTrue)")
    void endBeforeStartReturns400() throws Exception {
        AppointmentRequestDTO dto = requestDto();
        dto.setEndAt(START.minusSeconds(60));

        mockMvc.perform(
                        post("/api/v1/appointments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endAfterStart"));
    }

    @Test
    @DisplayName("POST com data no passado sem ciência vira 422")
    void pastDateReturns422() throws Exception {
        when(service.create(any()))
                .thenThrow(new BusinessException(BusinessErrorCode.PAST_DATE_NOT_CONFIRMED));

        mockMvc.perform(
                        post("/api/v1/appointments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(requestDto())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PAST_DATE_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("GET lista com envelope, paginação e parâmetros repetidos")
    void listReturnsEnvelopeWithPagination() throws Exception {
        when(service.list(any()))
                .thenReturn(new PageImpl<>(List.of(responseDto()), PageRequest.of(0, 10), 1));

        mockMvc.perform(
                        get("/api/v1/appointments")
                                .param("year", "2026")
                                .param("year", "2027")
                                .param("month", "8")
                                .param("type", "Entrevista")
                                .param("status", "Agendado")
                                .param("searchTerm", "entrevista"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.pageNumber").value(1));

        ArgumentCaptor<AppointmentSearchParams> params =
                ArgumentCaptor.forClass(AppointmentSearchParams.class);
        verify(service).list(params.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(2026, 2027), params.getValue().getYear());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(8), params.getValue().getMonth());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("Entrevista"), params.getValue().getType());
        org.junit.jupiter.api.Assertions.assertEquals(
                "entrevista", params.getValue().getSearchTerm());
    }

    @Test
    @DisplayName("GET /summary devolve a contagem por mês")
    void summaryReturnsCounts() throws Exception {
        when(service.summary(2026))
                .thenReturn(
                        List.of(
                                new AppointmentSummaryDTO(2026, 1, 3L),
                                new AppointmentSummaryDTO(2026, 2, 1L)));

        mockMvc.perform(get("/api/v1/appointments/summary").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].year").value(2026))
                .andExpect(jsonPath("$.data[0].month").value(1))
                .andExpect(jsonPath("$.data[0].count").value(3))
                .andExpect(jsonPath("$.data[1].count").value(1));
    }

    @Test
    @DisplayName("GET /summary sem o ano vira 400 REQUIRED_FIELD")
    void summaryWithoutYearReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("year"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("GET /{id} devolve o detalhe")
    void getReturnsDetail() throws Exception {
        when(service.get(ID)).thenReturn(responseDto());

        mockMvc.perform(get("/api/v1/appointments/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
    }

    @Test
    @DisplayName("GET /{id} inexistente vira 404")
    void getOfMissingReturns404() throws Exception {
        when(service.get(any())).thenThrow(NotFoundException.of("Compromisso", ID));

        mockMvc.perform(get("/api/v1/appointments/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT devolve o compromisso editado")
    void updateReturnsUpdated() throws Exception {
        when(service.update(eq(ID), any())).thenReturn(responseDto());

        AppointmentRequestDTO dto = requestDto();
        dto.setJustification("remarcado");

        mockMvc.perform(
                        put("/api/v1/appointments/{id}", ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /cancel exige justificativa no corpo")
    void cancelRequiresJustification() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/appointments/{id}/cancel", ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("justification"));
    }

    @Test
    @DisplayName("PATCH /cancel devolve o compromisso cancelado")
    void cancelReturnsCancelled() throws Exception {
        AppointmentResponseDTO cancelled = responseDto();
        cancelled.setStatus("Cancelado");
        when(service.cancel(ID, "cliente desistiu")).thenReturn(cancelled);

        mockMvc.perform(
                        patch("/api/v1/appointments/{id}/cancel", ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"justification\":\"cliente desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Cancelado"));
    }

    @Test
    @DisplayName("PATCH /complete devolve o compromisso concluído")
    void completeReturnsDone() throws Exception {
        AppointmentResponseDTO done = responseDto();
        done.setStatus("Concluído");
        when(service.complete(ID)).thenReturn(done);

        mockMvc.perform(patch("/api/v1/appointments/{id}/complete", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Concluído"));
    }

    @Test
    @DisplayName("PATCH /complete num cancelado vira 422")
    void completingCancelledReturns422() throws Exception {
        when(service.complete(ID))
                .thenThrow(
                        new BusinessException(
                                BusinessErrorCode.OPERATION_NOT_ALLOWED,
                                "Compromisso cancelado não pode ser concluído."));

        mockMvc.perform(patch("/api/v1/appointments/{id}/complete", ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("OPERATION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("DELETE devolve 204 sem corpo")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/{id}", ID)).andExpect(status().isNoContent());
        verify(service).delete(ID);
    }

    @Test
    @DisplayName("DELETE de compromisso inexistente vira 404")
    void deleteOfMissingReturns404() throws Exception {
        doThrow(NotFoundException.of("Compromisso", ID)).when(service).delete(ID);

        mockMvc.perform(delete("/api/v1/appointments/{id}", ID)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /history devolve a trilha paginada")
    void historyReturnsPagedTrail() throws Exception {
        when(service.history(eq(ID), anyInt(), anyInt()))
                .thenReturn(
                        new PageImpl<>(
                                List.of(
                                        new AppointmentHistoryDTO(
                                                UUID.randomUUID(),
                                                "EDITED",
                                                "motivo",
                                                START,
                                                UUID.randomUUID())),
                                PageRequest.of(0, 10),
                                1));

        mockMvc.perform(get("/api/v1/appointments/{id}/history", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("EDITED"))
                .andExpect(jsonPath("$.data[0].justification").value("motivo"))
                .andExpect(jsonPath("$.pagination.totalRecords").value(1));

        verify(service).history(ID, 1, 10);
    }
}
