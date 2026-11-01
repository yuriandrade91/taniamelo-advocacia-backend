package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Com banco de verdade porque é o que os mocks não enxergam: em transação {@code readOnly} o
 * registro de auditoria "é salvo" no repositório mockado e nunca chega à tabela.
 */
@DisplayName("Senha do INSS: só sai pela rota dedicada, e cada acesso fica registrado")
class ClientInssPasswordIntegrationTest extends PostgresIntegrationTest {

    private static final String INSS_PASSWORD = "senha-secreta-do-portal";

    @Autowired private WebApplicationContext context;
    @Autowired private ClientRepository clientRepository;
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
        Client client = TestFixtures.client(null, "Cliente da Senha");
        client.setCpf(TestFixtures.uniqueDocumentDigits());
        client.setInssPassword(INSS_PASSWORD);
        clientId = clientRepository.save(client).getId();
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("a aba de dados pessoais não entrega a senha")
    void personalDataNeverCarriesThePassword() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/personal-data", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Cliente da Senha"))
                .andExpect(jsonPath("$.data.inssPassword").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("revelar a senha deixa registro de leitura gravado no banco")
    void revealingThePasswordIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/inss-password", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inssPassword").value(INSS_PASSWORD));

        // O TenantResolutionFilter limpa o tenant ao fim da requisição; o teste ainda lê o banco.
        TenantContext.set("tenant_tania");
        long reads =
                auditLogRepository
                        .findByEntityNameAndEntityIdOrderByPerformedAtDesc(
                                "Client", clientId, Pageable.unpaged())
                        .stream()
                        .filter(entry -> entry.getAction() == AuditAction.READ)
                        .count();
        assertEquals(1, reads);
    }
}
