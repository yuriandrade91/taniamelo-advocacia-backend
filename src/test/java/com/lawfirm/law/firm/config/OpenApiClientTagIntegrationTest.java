package com.lawfirm.law.firm.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * O Swagger agrupa por tag, e a ficha é parte do cadastro do cliente: tem de aparecer junto das
 * demais rotas de {@code /clients}, não num grupo isolado no fim da página.
 */
@DisplayName("Swagger: a ficha do cliente fica na collection Cliente(s)")
class OpenApiClientTagIntegrationTest extends PostgresIntegrationTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .apply(springSecurity())
                        .defaultRequest(get("/").header("X-Tenant-Id", "tania"))
                        .build();
    }

    @Test
    @DisplayName("a rota da ficha usa a tag Cliente(s), e não existe grupo à parte para ela")
    void recordRouteIsUnderTheClientsTag() throws Exception {
        mockMvc.perform(get("/api/docs/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/v1/clients/{clientId}/record'].get.tags[0]")
                                .value("Cliente(s)"))
                .andExpect(jsonPath("$.tags[?(@.name=='Cliente - Ficha')]").isEmpty())
                .andExpect(jsonPath("$.tags[?(@.name=='Cliente(s)')].description").isNotEmpty());
    }
}
