package com.lawfirm.law.firm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.util.OfficeClock;
import java.time.LocalDate;
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
 * O card de aniversariantes depois de editar o cliente, <b>pelo HTTP</b>.
 *
 * <p><b>Por que existe, tendo um teste de serviço para o mesmo cenário.</b> O {@code
 * AniversariantesIntegrationTest} chama {@code service.update()} com o DTO já montado em Java. Isso
 * prova o serviço e o SQL, e não prova nada sobre a borda: desserialização do JSON, nome de
 * propriedade, binding. Se o campo que o cliente da API manda não casar com o DTO, o campo chega
 * {@code null} - e no PUT deste recurso {@code null} em certos campos significa "mantém o que está
 * gravado" ({@code ClientMapper#preservarObrigatoriosNaEdicao}). O resultado seria HTTP 200 com o
 * dado inalterado, que de fora é indistinguível de cache.
 *
 * <p>Foi exatamente essa a dúvida relatada: edição bem-sucedida e card antigo. Este teste fecha a
 * lacuna do outro, percorrendo PUT e GET de verdade.
 *
 * <p>MockMvc por {@code webAppContextSetup} sem a cadeia de segurança, como os outros testes de
 * controller deste pacote: o que está sob teste é o contrato do recurso, não a autenticação (que
 * tem o seu próprio teste em {@code SecuredEndpointsContractTest}).
 */
@DisplayName("Card de aniversariantes reflete a edição do cliente, via HTTP")
class CardAniversariantesAposEdicaoTest extends PostgresIntegrationTest {

    private static final String CPF = "529.982.247-25";

    @Autowired private WebApplicationContext wac;
    @Autowired private ClientRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        repository.deleteAll();
    }

    @AfterEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("PUT muda o celular e o GET seguinte do card já traz o novo")
    void putRefleteNoCard() throws Exception {
        LocalDate hoje = OfficeClock.today();
        UUID id = cadastrar(hoje);

        mockMvc.perform(get("/api/v1/clients/upcoming-birthdays").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mobilePhone").value("(31) 90000-0000"));

        // Corpo completo, como o PUT exige: fullName, birthDate, cpf, motherName, mobilePhone,
        // gender, benefit e situation são obrigatórios no contrato.
        String corpo =
                """
                {
                  "fullName": "Nome editado",
                  "birthDate": "%s",
                  "cpf": "%s",
                  "motherName": "Mãe do teste",
                  "mobilePhone": "(31) 91111-1111",
                  "gender": "Feminino",
                  "maritalStatus": "Solteiro(a)",
                  "benefit": "Aposentadoria por idade",
                  "situation": "Formulário preenchido",
                  "isWhatsapp": false
                }
                """
                        .formatted(
                                LocalDate.of(1980, hoje.getMonthValue(), hoje.getDayOfMonth()),
                                CPF);

        mockMvc.perform(
                        put("/api/v1/clients/{clientId}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                .andExpect(status().isOk());

        // A pergunta do relato: o GET seguinte traz o novo ou o antigo?
        mockMvc.perform(get("/api/v1/clients/upcoming-birthdays").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fullName").value("Nome editado"))
                .andExpect(jsonPath("$.data[0].mobilePhone").value("(31) 91111-1111"))
                .andExpect(jsonPath("$.data[0].isWhatsapp").value(false));
    }

    @Test
    @DisplayName("PATCH com celular é recusado com 400, em vez de aceitar e descartar")
    void patchRecusaCampoQueNaoAplica() throws Exception {
        LocalDate hoje = OfficeClock.today();
        UUID id = cadastrar(hoje);

        // ClientPatchRequestDTO só tem situation, benefit, clientType e notBillable. Antes da
        // anotação ignoreUnknown=false, este PATCH respondia 200 e descartava mobilePhone em
        // silêncio - origem do relato "editei o telefone e a Home mostra o antigo". Agora recusa.
        mockMvc.perform(
                        patch("/api/v1/clients/{clientId}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "mobilePhone": "(31) 92222-2222",
                                          "isWhatsapp": false,
                                          "situation": "Análise documental"
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        // E o dado segue intacto, como deve: a requisição foi recusada inteira.
        mockMvc.perform(get("/api/v1/clients/upcoming-birthdays").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mobilePhone").value("(31) 90000-0000"));
    }

    private UUID cadastrar(LocalDate quando) {
        Client cliente = new Client();
        cliente.setFullName("Nome original");
        cliente.setBirthDate(LocalDate.of(1980, quando.getMonthValue(), quando.getDayOfMonth()));
        cliente.setCpf(CPF);
        cliente.setMotherName("Mãe do teste");
        cliente.setMobilePhone("(31) 90000-0000");
        cliente.setIsWhatsapp(true);
        cliente.setInssPassword("senha-inss");
        cliente.setGender(Gender.FEMININO);
        cliente.setMaritalStatus(MaritalStatus.SOLTEIRO);
        cliente.setClientType(ClientType.POTENCIAL);
        cliente.setSituation(Situation.FORMULARIO_PREENCHIDO);
        cliente.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        return repository.saveAndFlush(cliente).getId();
    }
}
