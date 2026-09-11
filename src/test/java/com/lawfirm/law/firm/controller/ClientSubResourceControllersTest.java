package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lawfirm.law.firm.dto.ClientAddressBatchRequestDTO;
import com.lawfirm.law.firm.dto.ClientAddressRequestDTO;
import com.lawfirm.law.firm.dto.ClientAddressResponseDTO;
import com.lawfirm.law.firm.dto.ClientInterviewResponseDTO;
import com.lawfirm.law.firm.dto.ClientPaymentResponseDTO;
import com.lawfirm.law.firm.dto.ClientPersonalDataResponseDTO;
import com.lawfirm.law.firm.dto.ClientProfessionalDataResponseDTO;
import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.service.ClientAddressService;
import com.lawfirm.law.firm.service.ClientInterviewService;
import com.lawfirm.law.firm.service.ClientPaymentService;
import com.lawfirm.law.firm.service.ClientService;
import com.lawfirm.law.firm.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Sub-recursos de /clients/{id}: endereços, entrevistas, financeiro e abas de dados")
class ClientSubResourceControllersTest {

    private static final UUID CLIENT = TestFixtures.CLIENT_ID;
    private static final UUID SUB_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    @Mock private ClientAddressService addressService;
    @Mock private ClientInterviewService interviewService;
    @Mock private ClientPaymentService paymentService;
    @Mock private ClientService clientService;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private MockMvc mvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("ClientAddressController")
    class Addresses {

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = mvcFor(new ClientAddressController(addressService));
        }

        private ClientAddressResponseDTO responseDto() {
            ClientAddressResponseDTO dto = new ClientAddressResponseDTO();
            dto.setId(SUB_ID);
            dto.setAddressType("Residencial");
            dto.setStreet("Rua das Flores");
            dto.setCity("Belo Horizonte");
            dto.setState("MG");
            dto.setIsPrimary(true);
            return dto;
        }

        private ClientAddressRequestDTO requestDto() {
            ClientAddressRequestDTO dto = new ClientAddressRequestDTO();
            dto.setAddressType("Residencial");
            dto.setStreet("Rua das Flores");
            dto.setAddressNumber("10");
            dto.setNeighborhood("Centro");
            dto.setCity("Belo Horizonte");
            dto.setState("MG");
            dto.setZipCode("30000-000");
            return dto;
        }

        @Test
        @DisplayName("POST devolve 201 com o endereço criado")
        void createReturns201() throws Exception {
            when(addressService.create(eq(CLIENT), any())).thenReturn(responseDto());

            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/addresses", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDto())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.isPrimary").value(true))
                    .andExpect(jsonPath("$.data.addressType").value("Residencial"));
        }

        @Test
        @DisplayName("POST /batch devolve 201 com a lista criada")
        void createBatchReturns201() throws Exception {
            when(addressService.createAll(eq(CLIENT), any()))
                    .thenReturn(List.of(responseDto(), responseDto()));

            ClientAddressBatchRequestDTO body = new ClientAddressBatchRequestDTO();
            body.setAddresses(List.of(requestDto(), requestDto()));

            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/addresses/batch", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("POST /batch com lista vazia vira 400 sem chamar o service")
        void createBatchWithEmptyListIsRejected() throws Exception {
            ClientAddressBatchRequestDTO body = new ClientAddressBatchRequestDTO();
            body.setAddresses(List.of());

            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/addresses/batch", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verify(addressService, never()).createAll(any(), any());
        }

        @Test
        @DisplayName("POST /batch valida cada endereço da lista")
        void createBatchValidatesEachItem() throws Exception {
            ClientAddressRequestDTO invalid = requestDto();
            invalid.setStreet(null); // @NotBlank no item

            ClientAddressBatchRequestDTO body = new ClientAddressBatchRequestDTO();
            body.setAddresses(List.of(requestDto(), invalid));

            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/addresses/batch", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            verify(addressService, never()).createAll(any(), any());
        }

        @Test
        @DisplayName("GET lista com envelope e paginação")
        void listReturnsPagedEnvelope() throws Exception {
            when(addressService.list(eq(CLIENT), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of(responseDto()), PageRequest.of(0, 10), 1));

            mockMvc.perform(get("/api/v1/clients/{clientId}/addresses", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.pagination.pageNumber").value(1));

            verify(addressService).list(CLIENT, 1, 10);
        }

        @Test
        @DisplayName("GET /{addressId} devolve o detalhe")
        void getReturnsDetail() throws Exception {
            when(addressService.get(CLIENT, SUB_ID)).thenReturn(responseDto());

            mockMvc.perform(get("/api/v1/clients/{clientId}/addresses/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.street").value("Rua das Flores"));
        }

        @Test
        @DisplayName("PUT devolve o endereço atualizado")
        void updateReturnsUpdated() throws Exception {
            when(addressService.update(eq(CLIENT), eq(SUB_ID), any())).thenReturn(responseDto());

            mockMvc.perform(
                            put("/api/v1/clients/{clientId}/addresses/{id}", CLIENT, SUB_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDto())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("DELETE devolve 204")
        void deleteReturns204() throws Exception {
            mockMvc.perform(delete("/api/v1/clients/{clientId}/addresses/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isNoContent());
            verify(addressService).delete(CLIENT, SUB_ID);
        }

        @Test
        @DisplayName("cliente inexistente propaga 404")
        void missingClientReturns404() throws Exception {
            when(addressService.get(any(), any()))
                    .thenThrow(NotFoundException.of("Cliente", CLIENT));

            mockMvc.perform(get("/api/v1/clients/{clientId}/addresses/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("ClientInterviewController")
    class Interviews {

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = mvcFor(new ClientInterviewController(interviewService));
        }

        private ClientInterviewResponseDTO responseDto() {
            ClientInterviewResponseDTO dto = new ClientInterviewResponseDTO();
            dto.setId(SUB_ID);
            dto.setContent("<p>anotações</p>");
            dto.setDurationMinutes(60);
            dto.setOccurredAt(Instant.parse("2026-03-01T10:00:00Z"));
            return dto;
        }

        @Test
        @DisplayName("POST devolve 201")
        void createReturns201() throws Exception {
            when(interviewService.create(eq(CLIENT), any())).thenReturn(responseDto());

            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/interviews", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"content\":\"<p>anotações</p>\",\"durationMinutes\":60}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.durationMinutes").value(60));
        }

        @Test
        @DisplayName("POST sem conteúdo vira 400")
        void createWithoutContentReturns400() throws Exception {
            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/interviews", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("content"));
        }

        @Test
        @DisplayName("GET lista paginada")
        void listReturnsPaged() throws Exception {
            when(interviewService.list(eq(CLIENT), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of(responseDto()), PageRequest.of(0, 10), 1));

            mockMvc.perform(get("/api/v1/clients/{clientId}/interviews", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].content").value("<p>anotações</p>"));
        }

        @Test
        @DisplayName("GET /{interviewId} devolve o detalhe")
        void getReturnsDetail() throws Exception {
            when(interviewService.get(CLIENT, SUB_ID)).thenReturn(responseDto());

            mockMvc.perform(get("/api/v1/clients/{clientId}/interviews/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(SUB_ID.toString()));
        }

        @Test
        @DisplayName("PUT atualiza e DELETE devolve 204")
        void updateAndDelete() throws Exception {
            when(interviewService.update(eq(CLIENT), eq(SUB_ID), any())).thenReturn(responseDto());

            mockMvc.perform(
                            put("/api/v1/clients/{clientId}/interviews/{id}", CLIENT, SUB_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"content\":\"novo\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/v1/clients/{clientId}/interviews/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isNoContent());
            verify(interviewService).delete(CLIENT, SUB_ID);
        }

        @Test
        @DisplayName("entrevista inexistente propaga 404")
        void missingInterviewReturns404() throws Exception {
            doThrow(NotFoundException.of("Entrevista", SUB_ID))
                    .when(interviewService)
                    .delete(CLIENT, SUB_ID);

            mockMvc.perform(delete("/api/v1/clients/{clientId}/interviews/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("ClientPaymentController")
    class Payments {

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = mvcFor(new ClientPaymentController(paymentService));
        }

        private ClientPaymentResponseDTO responseDto() {
            ClientPaymentResponseDTO dto = new ClientPaymentResponseDTO();
            dto.setId(SUB_ID);
            dto.setDescription("Parcela 1/3");
            dto.setAmount(new BigDecimal("500.00"));
            dto.setStatus("Pendente");
            dto.setDueDate(LocalDate.of(2026, 9, 10));
            dto.setOverdue(false);
            return dto;
        }

        @Test
        @DisplayName("POST devolve 201 com a parcela pendente")
        void createReturns201() throws Exception {
            when(paymentService.create(eq(CLIENT), any())).thenReturn(responseDto());

            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/payments", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"description\":\"Parcela 1/3\",\"amount\":500.00,"
                                                    + "\"dueDate\":\"2026-09-10\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("Pendente"))
                    .andExpect(jsonPath("$.data.overdue").value(false));
        }

        @Test
        @DisplayName("POST sem valor obrigatório vira 400")
        void createWithoutAmountReturns400() throws Exception {
            mockMvc.perform(
                            post("/api/v1/clients/{clientId}/payments", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("GET lista paginada com overdue calculado")
        void listReturnsPaged() throws Exception {
            ClientPaymentResponseDTO overdue = responseDto();
            overdue.setOverdue(true);
            when(paymentService.list(eq(CLIENT), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of(overdue), PageRequest.of(0, 10), 1));

            mockMvc.perform(get("/api/v1/clients/{clientId}/payments", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].overdue").value(true));
        }

        @Test
        @DisplayName("GET /{paymentId} devolve o detalhe")
        void getReturnsDetail() throws Exception {
            when(paymentService.get(CLIENT, SUB_ID)).thenReturn(responseDto());

            mockMvc.perform(get("/api/v1/clients/{clientId}/payments/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.description").value("Parcela 1/3"));
        }

        @Test
        @DisplayName("PATCH atualiza e DELETE devolve 204")
        void patchAndDelete() throws Exception {
            ClientPaymentResponseDTO paid = responseDto();
            paid.setStatus("Pago");
            when(paymentService.update(eq(CLIENT), eq(SUB_ID), any())).thenReturn(paid);

            mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .patch(
                                            "/api/v1/clients/{clientId}/payments/{id}",
                                            CLIENT,
                                            SUB_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"Pago\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("Pago"));

            mockMvc.perform(delete("/api/v1/clients/{clientId}/payments/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isNoContent());
            verify(paymentService).delete(CLIENT, SUB_ID);
        }

        @Test
        @DisplayName("parcela inexistente propaga 404")
        void missingPaymentReturns404() throws Exception {
            when(paymentService.get(any(), any()))
                    .thenThrow(NotFoundException.of("Parcela", SUB_ID));

            mockMvc.perform(get("/api/v1/clients/{clientId}/payments/{id}", CLIENT, SUB_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("ClientPersonalDataController e ClientProfessionalDataController")
    class DataTabs {

        @Test
        @DisplayName("GET/PUT de dados pessoais")
        void personalDataEndpoints() throws Exception {
            MockMvc mockMvc = mvcFor(new ClientPersonalDataController(clientService));

            ClientPersonalDataResponseDTO dto = new ClientPersonalDataResponseDTO();
            dto.setClientId(CLIENT);
            dto.setFullName("Maria da Silva");
            dto.setAge(45);
            dto.setGender(Gender.FEMININO);
            when(clientService.getPersonalData(CLIENT)).thenReturn(dto);
            when(clientService.updatePersonalData(eq(CLIENT), any())).thenReturn(dto);

            mockMvc.perform(get("/api/v1/clients/{clientId}/personal-data", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Maria da Silva"))
                    .andExpect(jsonPath("$.data.age").value(45))
                    .andExpect(jsonPath("$.data.gender").value("Feminino"));

            mockMvc.perform(
                            put("/api/v1/clients/{clientId}/personal-data", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"fullName\":\"Maria da Silva\",\"birthDate\":\"1980-05-20\","
                                                    + "\"cpf\":\"529.982.247-25\",\"motherName\":\"Joana\","
                                                    + "\"mobilePhone\":\"+5511999999999\",\"gender\":\"Feminino\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("cliente inexistente na aba de dados pessoais vira 404")
        void personalDataOfMissingClientReturns404() throws Exception {
            MockMvc mockMvc = mvcFor(new ClientPersonalDataController(clientService));
            when(clientService.getPersonalData(any()))
                    .thenThrow(NotFoundException.of("Cliente", CLIENT));

            mockMvc.perform(get("/api/v1/clients/{clientId}/personal-data", CLIENT))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET/PUT de dados profissionais, com total em meses derivado")
        void professionalDataEndpoints() throws Exception {
            MockMvc mockMvc = mvcFor(new ClientProfessionalDataController(clientService));

            ClientProfessionalDataResponseDTO dto = new ClientProfessionalDataResponseDTO();
            dto.setClientId(CLIENT);
            dto.setProfession("Costureira");
            dto.setContributionTime("10 anos");
            dto.setContributionInMonths(120);
            when(clientService.getProfessionalData(CLIENT)).thenReturn(dto);
            when(clientService.updateProfessionalData(eq(CLIENT), any())).thenReturn(dto);

            mockMvc.perform(get("/api/v1/clients/{clientId}/professional-data", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.profession").value("Costureira"))
                    .andExpect(jsonPath("$.data.contributionInMonths").value(120));

            mockMvc.perform(
                            put("/api/v1/clients/{clientId}/professional-data", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"profession\":\"Costureira\",\"contributionTime\":\"10 anos\","
                                                    + "\"inssPassword\":\"senha\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.contributionInMonths").value(120));
        }

        @Test
        @DisplayName("PUT de dados profissionais sem a senha do INSS é aceito")
        void professionalDataWithoutInssPasswordIsAccepted() throws Exception {
            // A senha deixou de ser obrigatória na edição quando saiu do GET: quem edita a aba não
            // a recebe mais, logo não tem como devolvê-la. Ausente significa "mantém a gravada" -
            // e o teste do service garante que ela não é apagada.
            MockMvc mockMvc = mvcFor(new ClientProfessionalDataController(clientService));

            mockMvc.perform(
                            put("/api/v1/clients/{clientId}/professional-data", CLIENT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"profession\":\"Costureira\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("cliente inexistente na aba profissional vira 404")
        void professionalDataOfMissingClientReturns404() throws Exception {
            MockMvc mockMvc = mvcFor(new ClientProfessionalDataController(clientService));
            when(clientService.getProfessionalData(any()))
                    .thenThrow(NotFoundException.of("Cliente", CLIENT));

            mockMvc.perform(get("/api/v1/clients/{clientId}/professional-data", CLIENT))
                    .andExpect(status().isNotFound());
        }
    }
}
