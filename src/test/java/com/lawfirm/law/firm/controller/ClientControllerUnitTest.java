package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lawfirm.law.firm.dto.ClientCreateRequestDTO;
import com.lawfirm.law.firm.dto.ClientDetailsDTO;
import com.lawfirm.law.firm.dto.ClientListResponseDTO;
import com.lawfirm.law.firm.dto.ClientSituationHistoryDTO;
import com.lawfirm.law.firm.dto.ClientUpdateRequestDTO;
import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.service.ClientPatchOutcome;
import com.lawfirm.law.firm.service.ClientService;
import com.lawfirm.law.firm.support.TestFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@DisplayName("ClientController: contrato HTTP do cadastro de clientes")
class ClientControllerUnitTest {

    @Mock private ClientService clientService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ClientController(clientService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private ClientDetailsDTO detailsDto() {
        ClientDetailsDTO dto = new ClientDetailsDTO();
        dto.setId(TestFixtures.CLIENT_ID);
        dto.setFullName("Maria da Silva");
        dto.setEmail("maria@x.com");
        dto.setGender(Gender.FEMININO);
        dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        dto.setSituation(Situation.FORMULARIO_PREENCHIDO);
        return dto;
    }

    private ClientCreateRequestDTO createRequest() {
        ClientCreateRequestDTO dto = new ClientCreateRequestDTO();
        dto.setFullName("Maria da Silva");
        dto.setBirthDate(LocalDate.of(1980, 5, 20));
        dto.setCpf("529.982.247-25");
        dto.setMotherName("Joana");
        dto.setMobilePhone("+5511999999999");
        dto.setInssPassword("senha");
        dto.setGender(Gender.FEMININO);
        dto.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        dto.setSituation(Situation.FORMULARIO_PREENCHIDO);
        return dto;
    }

    @Nested
    @DisplayName("POST /api/v1/clients")
    class Create {

        @Test
        @DisplayName("201 com Location e envelope de sucesso")
        void createsAndReturnsLocation() throws Exception {
            when(clientService.create(any())).thenReturn(detailsDto());

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(createRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(
                            header().string(
                                            "Location",
                                            "http://localhost/api/v1/clients/"
                                                    + TestFixtures.CLIENT_ID))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(TestFixtures.CLIENT_ID.toString()))
                    .andExpect(jsonPath("$.data.fullName").value("Maria da Silva"))
                    .andExpect(jsonPath("$.data.gender").value("Feminino"))
                    .andExpect(jsonPath("$.data.situation").value("Formulário preenchido"));
        }

        @Test
        @DisplayName("corpo com campos obrigatórios faltando vira 400 no envelope de erro")
        void missingRequiredFieldsReturn400() throws Exception {
            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        @DisplayName("CPF inválido é barrado pela validação de bean")
        void invalidCpfReturns400() throws Exception {
            ClientCreateRequestDTO dto = createRequest();
            dto.setCpf("000.000.000-00");

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("cpf"));
        }

        @Test
        @DisplayName("enum desconhecido no corpo vira 400 INVALID_ENUM_VALUE apontando o campo")
        void unknownEnumInBodyReturns400() throws Exception {
            String body =
                    "{\"fullName\":\"X\",\"birthDate\":\"1980-01-01\",\"cpf\":\"529.982.247-25\","
                            + "\"motherName\":\"Y\",\"mobilePhone\":\"1\",\"inssPassword\":\"p\","
                            + "\"gender\":\"Masculino\",\"benefit\":\"Aposentadoria por idade\","
                            + "\"situation\":\"Situação que não existe\"}";

            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM_VALUE"))
                    .andExpect(jsonPath("$.errors[0].field").value("situation"));
        }

        @Test
        @DisplayName("JSON malformado vira 400 INVALID_REQUEST_BODY")
        void malformedJsonReturns400() throws Exception {
            mockMvc.perform(
                            post("/api/v1/clients")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{isso não é json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_REQUEST_BODY"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clients")
    class ListClients {

        @BeforeEach
        void stubList() {
            when(clientService.listSummary(
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any()))
                    .thenReturn(
                            new PageImpl<>(
                                    List.of(new ClientListResponseDTO()),
                                    PageRequest.of(0, 10),
                                    1));
        }

        @Test
        @DisplayName("devolve envelope com data e bloco de paginação")
        void returnsEnvelopeWithPagination() throws Exception {
            mockMvc.perform(get("/api/v1/clients"))
                    .andExpect(status().isOk())
                    .andExpect(content -> {})
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.pagination.pageNumber").value(1))
                    .andExpect(jsonPath("$.pagination.pageSize").value(10))
                    .andExpect(jsonPath("$.pagination.totalRecords").value(1))
                    .andExpect(jsonPath("$.pagination.totalPages").value(1))
                    .andExpect(jsonPath("$.pagination.hasNextPage").value(false))
                    .andExpect(jsonPath("$.pagination.hasPreviousPage").value(false));
        }

        @Test
        @DisplayName("usa os defaults de página quando nada é informado")
        void usesDefaultPaging() throws Exception {
            mockMvc.perform(get("/api/v1/clients")).andExpect(status().isOk());

            verify(clientService)
                    .listSummary(
                            eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), isNull(),
                            isNull());
        }

        @Test
        @DisplayName("filtros repetidos de benefício e situação viram listas de enum")
        void repeatedEnumFiltersBecomeLists() throws Exception {
            mockMvc.perform(
                            get("/api/v1/clients")
                                    .param("pageNumber", "2")
                                    .param("pageSize", "5")
                                    .param("searchTerm", "maria")
                                    .param("benefitType", "Aposentadoria rural")
                                    .param("benefitType", "APOSENTADORIA_ESPECIAL")
                                    .param("situation", "Análise documental")
                                    .param("clientType", "Verificado")
                                    .param("clientType", "POTENCIAL")
                                    .param("createdFrom", "2026-01-01")
                                    .param("createdTo", "2026-12-31"))
                    .andExpect(status().isOk());

            ArgumentCaptor<List<BenefitType>> benefits = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<List<Situation>> situations = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<List<ClientType>> clientTypes = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);

            verify(clientService)
                    .listSummary(
                            eq(2),
                            eq(5),
                            eq("maria"),
                            benefits.capture(),
                            situations.capture(),
                            clientTypes.capture(),
                            from.capture(),
                            to.capture());

            org.junit.jupiter.api.Assertions.assertEquals(
                    List.of(BenefitType.APOSENTADORIA_RURAL, BenefitType.APOSENTADORIA_ESPECIAL),
                    benefits.getValue());
            org.junit.jupiter.api.Assertions.assertEquals(
                    List.of(Situation.ANALISE_DOCUMENTAL), situations.getValue());
            // Label e nome da constante no mesmo filtro: os dois precisam resolver,
            // porque o front manda a chave e o Swagger documenta o label.
            org.junit.jupiter.api.Assertions.assertEquals(
                    List.of(ClientType.VERIFICADO, ClientType.POTENCIAL), clientTypes.getValue());
            org.junit.jupiter.api.Assertions.assertEquals(
                    Instant.parse("2026-01-01T00:00:00Z"), from.getValue());
            org.junit.jupiter.api.Assertions.assertEquals(
                    Instant.parse("2026-12-31T23:59:59.999999999Z"), to.getValue());
        }

        @Test
        @DisplayName("benefício desconhecido vira 400 INVALID_ENUM_VALUE no campo benefitType")
        void unknownBenefitFilterReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/clients").param("benefitType", "not-a-benefit"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors[0].field").value("benefitType"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM_VALUE"));
        }

        @Test
        @DisplayName("situação desconhecida vira 400 no campo situation")
        void unknownSituationFilterReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/clients").param("situation", "xpto"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("situation"));
        }

        @Test
        @DisplayName("data inválida no filtro vira 400 INVALID_DATE")
        void invalidDateFilterReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/clients").param("createdFrom", "31/12/2026"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("createdFrom"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_DATE"));
        }

        @Test
        @DisplayName("página não numérica vira 400 INVALID_PARAMETER")
        void nonNumericPageReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/clients").param("pageNumber", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PARAMETER"));
        }
    }

    @Nested
    @DisplayName("GET/PUT/DELETE /api/v1/clients/{id}")
    class ById {

        @Test
        @DisplayName("200 quando o cliente existe")
        void getByIdReturnsClient() throws Exception {
            when(clientService.findById(TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(detailsDto()));

            mockMvc.perform(get("/api/v1/clients/{id}", TestFixtures.CLIENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Maria da Silva"));
        }

        @Test
        @DisplayName("404 no envelope quando não existe")
        void getByIdReturns404() throws Exception {
            when(clientService.findById(any())).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/clients/{id}", TestFixtures.CLIENT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("id que não é UUID vira 400")
        void nonUuidIdReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/clients/{id}", "nao-e-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PARAMETER"));
        }

        @Test
        @DisplayName("PUT devolve o cliente atualizado")
        void updateReturnsUpdatedClient() throws Exception {
            when(clientService.update(eq(TestFixtures.CLIENT_ID), any())).thenReturn(detailsDto());

            ClientUpdateRequestDTO body = new ClientUpdateRequestDTO();
            body.setFullName("Maria da Silva");
            body.setBirthDate(LocalDate.of(1980, 5, 20));
            body.setCpf("529.982.247-25");
            body.setMotherName("Joana");
            body.setMobilePhone("+5511999999999");
            body.setInssPassword("senha");
            body.setGender(Gender.FEMININO);
            body.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
            body.setSituation(Situation.FORMULARIO_PREENCHIDO);

            mockMvc.perform(
                            put("/api/v1/clients/{id}", TestFixtures.CLIENT_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.fullName").value("Maria da Silva"));
        }

        @Test
        @DisplayName("DELETE devolve a mensagem de confirmação")
        void deleteReturnsMessage() throws Exception {
            mockMvc.perform(delete("/api/v1/clients/{id}", TestFixtures.CLIENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.message").value("Cliente excluído com sucesso."));

            verify(clientService).delete(TestFixtures.CLIENT_ID);
        }

        @Test
        @DisplayName("DELETE de cliente inexistente propaga 404")
        void deleteOfMissingClientReturns404() throws Exception {
            doThrow(NotFoundException.of("Cliente", TestFixtures.CLIENT_ID))
                    .when(clientService)
                    .delete(TestFixtures.CLIENT_ID);

            mockMvc.perform(delete("/api/v1/clients/{id}", TestFixtures.CLIENT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/clients/{id} - mensagem com concordância PT-BR")
    class Patch {

        private void stubOutcome(
                boolean situation, boolean benefit, boolean type, boolean billable) {
            when(clientService.patch(any(), any()))
                    .thenReturn(new ClientPatchOutcome(situation, benefit, type, billable));
        }

        @ParameterizedTest(name = "{4}")
        @CsvSource({
            "false,false,false,false,Nenhuma alteração realizada!",
            "true,false,false,false,Situação atualizada com sucesso!",
            "false,true,false,false,Benefício atualizado com sucesso!",
            "false,false,true,false,Tipo de cliente atualizado com sucesso!",
            "false,false,false,true,Arrecadação atualizada com sucesso!",
            "true,false,false,true,Situação e Arrecadação atualizadas com sucesso!",
            "true,true,false,false,Situação e Benefício atualizados com sucesso!",
            "false,true,true,false,Benefício e Tipo de cliente atualizados com sucesso!",
            "true,true,true,true,'Situação, Benefício, Tipo de cliente e Arrecadação atualizados com sucesso!'"
        })
        void buildsTheRightMessage(
                boolean situation,
                boolean benefit,
                boolean type,
                boolean billable,
                String expectedMessage)
                throws Exception {
            stubOutcome(situation, benefit, type, billable);

            mockMvc.perform(
                            patch("/api/v1/clients/{id}", TestFixtures.CLIENT_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"situation\":\"Análise documental\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.message").value(expectedMessage));
        }

        @Test
        @DisplayName("corpo vazio é aceito (PATCH não exige validação de bean)")
        void emptyBodyIsAccepted() throws Exception {
            stubOutcome(false, false, false, false);

            mockMvc.perform(
                            patch("/api/v1/clients/{id}", TestFixtures.CLIENT_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/clients/{id}/situation-history")
    class SituationHistory {

        @Test
        @DisplayName("devolve a lista paginada no envelope")
        void returnsPagedHistory() throws Exception {
            when(clientService.historyByClientId(
                            eq(TestFixtures.CLIENT_ID),
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(
                            new PageImpl<>(
                                    List.of(new ClientSituationHistoryDTO()),
                                    PageRequest.of(1, 5),
                                    12));

            mockMvc.perform(
                            get("/api/v1/clients/{id}/situation-history", TestFixtures.CLIENT_ID)
                                    .param("pageNumber", "2")
                                    .param("pageSize", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.pagination.pageNumber").value(2))
                    .andExpect(jsonPath("$.pagination.totalRecords").value(12))
                    .andExpect(jsonPath("$.pagination.totalPages").value(3))
                    .andExpect(jsonPath("$.pagination.hasNextPage").value(true))
                    .andExpect(jsonPath("$.pagination.hasPreviousPage").value(true));

            verify(clientService).historyByClientId(TestFixtures.CLIENT_ID, 2, 5);
        }

        @Test
        @DisplayName("cliente inexistente propaga 404")
        void missingClientReturns404() throws Exception {
            when(clientService.historyByClientId(
                            any(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.anyInt()))
                    .thenThrow(NotFoundException.of("Cliente", TestFixtures.CLIENT_ID));

            mockMvc.perform(get("/api/v1/clients/{id}/situation-history", TestFixtures.CLIENT_ID))
                    .andExpect(status().isNotFound());
        }
    }
}
