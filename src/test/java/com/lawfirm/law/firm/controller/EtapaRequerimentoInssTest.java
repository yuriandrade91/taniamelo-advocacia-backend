package com.lawfirm.law.firm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.RequestStage;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * A etapa do requerimento no INSS, de ponta a ponta pelo HTTP.
 *
 * <p>A esteira do INSS é um campo separado de {@code situation} de propósito: aquela é o andamento
 * dentro do escritório, esta é o andamento do requerimento no órgão. As duas correm em paralelo, e
 * o teste abaixo monta exatamente esse caso - cliente com situação igual e etapas diferentes - para
 * que um eventual colapso das duas num campo só quebre aqui.
 *
 * <p>Vai pelo HTTP, e não pelo service, pela lição do PATCH que aceitava {@code mobilePhone} e o
 * descartava em silêncio: teste de serviço com DTO montado em Java não enxerga a borda onde o
 * contrato se perde.
 */
@DisplayName("Etapa do requerimento no INSS: listagem, filtro, detalhe e PATCH")
class EtapaRequerimentoInssTest extends PostgresIntegrationTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private ClientRepository repository;

    private MockMvc mockMvc;
    private UUID emExigencia;
    private UUID semRequerimento;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        repository.deleteAll();
        emExigencia = cadastrar("Em exigência", "871.222.222-41", RequestStage.EXIGENCIA);
        cadastrar("Deferido", "871.223.579-29", RequestStage.DEFERIMENTO);
        // Nulo é estado legítimo: cliente que ainda não protocolou requerimento.
        semRequerimento = cadastrar("Sem requerimento", "871.224.936-01", null);
    }

    @AfterEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("a listagem devolve a etapa, e o filtro traz só quem está nela")
    void listaEFiltra() throws Exception {
        mockMvc.perform(get("/api/v1/clients").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data[?(@.fullName=='Em exigência')].requestStage")
                                .value("Exigência"));

        mockMvc.perform(get("/api/v1/clients").param("requestStage", "Exigência"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Em exigência"));
    }

    @Test
    @DisplayName("quem não protocolou não aparece em filtro de etapa nenhum")
    void semRequerimentoNaoEntraNoFiltro() throws Exception {
        for (RequestStage etapa : RequestStage.values()) {
            mockMvc.perform(get("/api/v1/clients").param("requestStage", etapa.getLabel()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.fullName=='Sem requerimento')]").isEmpty());
        }
    }

    @Test
    @DisplayName("o detalhe do cliente traz a etapa, inclusive nula")
    void detalheTrazAEtapa() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{clientId}", emExigencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestStage").value("Exigência"));

        mockMvc.perform(get("/api/v1/clients/{clientId}", semRequerimento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestStage").doesNotExist());
    }

    @Test
    @DisplayName("PATCH avança a etapa sem tocar na situação do escritório")
    void patchAvancaAEtapa() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", emExigencia)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestStage\": \"Cumprimento de exigência\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clients/{clientId}", emExigencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestStage").value("Cumprimento de exigência"))
                // A esteira do escritório não pode ter sido arrastada junto.
                .andExpect(jsonPath("$.data.situation").value("Formulário preenchido"));
    }

    @Test
    @DisplayName("PATCH com etapa inexistente é recusado, em vez de gravar lixo")
    void patchRecusaEtapaInvalida() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", emExigencia)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestStage\": \"Arquivado\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cada mudança de etapa vira uma entrada no histórico, com DE e PARA")
    void mudancaGeraHistorico() throws Exception {
        avancar(emExigencia, "Cumprimento de exigência");

        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", emExigencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].previousStage").value("Exigência"))
                .andExpect(jsonPath("$.data[0].currentStage").value("Cumprimento de exigência"))
                // Primeira transição: não havia etapa anterior cronometrada.
                .andExpect(jsonPath("$.data[0].diasNaEtapaAnterior").doesNotExist())
                // A esteira do INSS não tem funil, logo não tem retrocesso a sinalizar.
                .andExpect(jsonPath("$.data[0].retrocesso").doesNotExist());
    }

    @Test
    @DisplayName("mais recente primeiro, e a partir da segunda transição há tempo na etapa")
    void histiricoOrdenadoEComTempo() throws Exception {
        avancar(emExigencia, "Cumprimento de exigência");
        avancar(emExigencia, "Deferimento");

        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", emExigencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].currentStage").value("Deferimento"))
                .andExpect(jsonPath("$.data[1].currentStage").value("Cumprimento de exigência"))
                // A segunda transição sabe quando a anterior começou - é o que responde
                // "está em exigência há quanto tempo". Aqui as duas ocorrem no mesmo
                // instante, então zero; o que importa é o campo estar preenchido.
                .andExpect(jsonPath("$.data[0].diasNaEtapaAnterior").value(0));
    }

    @Test
    @DisplayName("as duas esteiras têm históricos separados")
    void historicosNaoSeMisturam() throws Exception {
        avancar(emExigencia, "Deferimento");

        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", emExigencia))
                .andExpect(jsonPath("$.data.length()").value(1));
        // A esteira do escritório não foi tocada, então o histórico dela segue vazio.
        mockMvc.perform(get("/api/v1/clients/{clientId}/situation-history", emExigencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName(
            "entrar em exigência, indeferimento ou recurso sem justificativa é recusado, e nada muda")
    void etapaQueExigeJustificativaSemElaERecusada() throws Exception {
        for (String etapa : List.of("Exigência", "Indeferimento", "Recurso")) {
            mockMvc.perform(
                            patch("/api/v1/clients/{clientId}", semRequerimento)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"requestStage\": \"" + etapa + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("requestStageJustification"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED_FIELD"));
        }
        // Em branco é o mesmo que ausente: espaço não explica nada.
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", semRequerimento)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"requestStage\": \"Exigência\","
                                                + " \"requestStageJustification\": \"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/clients/{clientId}", semRequerimento))
                .andExpect(jsonPath("$.data.requestStage").doesNotExist());
        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", semRequerimento))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName(
            "com justificativa a etapa muda, e o motivo vai para o histórico sem espaços nas pontas")
    void justificativaVaiParaOHistorico() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", emExigencia)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"requestStage\": \"Indeferimento\","
                                                + " \"requestStageJustification\":"
                                                + " \"  Falta de carência.  \"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", emExigencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentStage").value("Indeferimento"))
                .andExpect(jsonPath("$.data[0].justification").value("Falta de carência."));
    }

    @Test
    @DisplayName("nas demais etapas a justificativa é opcional")
    void demaisEtapasNaoExigemJustificativa() throws Exception {
        avancar(emExigencia, "Cumprimento de exigência");

        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", emExigencia))
                .andExpect(jsonPath("$.data[0].currentStage").value("Cumprimento de exigência"))
                .andExpect(jsonPath("$.data[0].justification").doesNotExist());
    }

    @Test
    @DisplayName("repetir a etapa gravada não é transição e não pede justificativa")
    void mesmaEtapaNaoPedeJustificativa() throws Exception {
        avancar(emExigencia, "Exigência");

        mockMvc.perform(get("/api/v1/clients/{clientId}/request-stage-history", emExigencia))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("justificativa sem etapa é recusada, em vez de descartada em silêncio")
    void justificativaSemEtapaERecusada() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", emExigencia)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestStageJustification\": \"sem etapa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CONFLICTING_PARAMETERS"));
    }

    private void avancar(UUID clientId, String etapa) throws Exception {
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", clientId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestStage\": \"" + etapa + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID cadastrar(String nome, String cpf, RequestStage etapa) {
        Client cliente = new Client();
        cliente.setFullName(nome);
        cliente.setBirthDate(LocalDate.of(1975, 3, 14));
        cliente.setCpf(cpf);
        cliente.setMotherName("Mãe do teste");
        cliente.setMobilePhone("(31) 90000-0000");
        cliente.setInssPassword("senha-inss");
        cliente.setGender(Gender.FEMININO);
        cliente.setMaritalStatus(MaritalStatus.SOLTEIRO);
        cliente.setClientType(ClientType.POTENCIAL);
        // Mesma situação de escritório nos três: o que varia é só a etapa no INSS.
        cliente.setSituation(Situation.FORMULARIO_PREENCHIDO);
        cliente.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        cliente.setRequestStage(etapa);
        return repository.saveAndFlush(cliente).getId();
    }
}
