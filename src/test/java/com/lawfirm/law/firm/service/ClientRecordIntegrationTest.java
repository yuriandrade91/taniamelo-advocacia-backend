package com.lawfirm.law.firm.service;

import static com.lawfirm.law.firm.service.ClientRecordFixtures.textOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.AuditLog;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import com.lawfirm.law.firm.dto.ClientPaymentRequestDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * A rota de ponta a ponta, com banco real: é aqui que se prova que o registro de auditoria chega ao
 * banco e que o recorte por papel vale na resposta, não só no renderer.
 */
@DisplayName("GET /clients/{clientId}/record: PDF, recorte por papel e auditoria")
class ClientRecordIntegrationTest extends PostgresIntegrationTest {

    private static final String INSS_PASSWORD = "senha-secreta-do-portal";

    @Autowired private WebApplicationContext context;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ClientPaymentService paymentService;
    @Autowired private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .apply(springSecurity())
                        .defaultRequest(get("/").header("X-Tenant-Id", "tania"))
                        .build();

        Client client = new Client();
        client.setFullName("Ficha Teste Ação");
        client.setBirthDate(LocalDate.of(1990, 1, 1));
        client.setCpf(uniqueCpf());
        client.setMotherName("Mãe Teste");
        client.setMobilePhone("+5511900000000");
        client.setInssPassword(INSS_PASSWORD);
        client.setGender(Gender.FEMININO);
        client.setMaritalStatus(MaritalStatus.SOLTEIRO);
        client.setClientType(ClientType.POTENCIAL);
        client.setSituation(Situation.FORMULARIO_PREENCHIDO);
        client.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        clientId = clientRepository.save(client).getId();

        ClientPaymentRequestDTO payment = new ClientPaymentRequestDTO();
        payment.setDescription("Parcela do retroativo");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setDueDate(LocalDate.of(2026, 12, 1));
        paymentService.create(clientId, payment);
    }

    private static String uniqueCpf() {
        return String.format(
                "%011d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000_000L);
    }

    private String downloadedText() throws Exception {
        byte[] pdf =
                mockMvc.perform(get("/api/v1/clients/{id}/record", clientId))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                org.hamcrest.Matchers.containsString(
                                                        "attachment; filename")))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        // O TenantResolutionFilter limpa o tenant ao fim da requisição; o teste ainda lê o banco.
        TenantContext.set("tenant_tania");
        return textOf(pdf);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF baixa a ficha sem o financeiro e sem a senha do INSS")
    void staffGetsRecordWithoutMoney() throws Exception {
        String text = downloadedText();

        assertTrue(text.contains("Ficha Teste Ação"), text);
        assertFalse(text.contains("Parcela do retroativo"), text);
        assertFalse(text.contains(INSS_PASSWORD), text);
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    @DisplayName("SUPPORT também não vê o financeiro: o corte é o mesmo das parcelas")
    void supportGetsRecordWithoutMoney() throws Exception {
        assertFalse(downloadedText().contains("Parcela do retroativo"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN vê o financeiro, mas nem ele recebe a senha do INSS")
    void adminGetsMoneyButNeverThePassword() throws Exception {
        String text = downloadedText();

        assertTrue(text.contains("Parcela do retroativo"), text);
        assertFalse(text.contains(INSS_PASSWORD), text);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("o download deixa registro de leitura na auditoria, já gravado no banco")
    void downloadIsAudited() throws Exception {
        downloadedText();

        List<AuditLog> reads =
                auditLogRepository
                        .findByEntityNameAndEntityIdOrderByPerformedAtDesc(
                                "Client", clientId, Pageable.unpaged())
                        .stream()
                        .filter(entry -> entry.getAction() == AuditAction.READ)
                        .toList();
        assertEquals(1, reads.size());
        assertEquals("Client record PDF exported", reads.get(0).getDetail());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("cliente inexistente devolve 404")
    void unknownClientIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/record", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sem autenticação não baixa")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/record", clientId))
                .andExpect(status().isUnauthorized());
    }
}
