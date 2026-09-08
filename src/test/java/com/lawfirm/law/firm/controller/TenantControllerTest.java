package com.lawfirm.law.firm.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import com.lawfirm.law.firm.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantController: resolução pública por slug e tenant corrente")
class TenantControllerTest {

    @Mock private TenantRepository tenantRepository;

    private MockMvc mockMvc;
    private TenancyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TenancyProperties();
        properties.setDefaultSchema("tenant_tania");
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));
        mockMvc =
                MockMvcBuilders.standaloneSetup(new TenantController(tenantRepository, properties))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /resolve devolve o UUID público, o slug e a razão social")
    void resolveReturnsPublicIdentity() throws Exception {
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "tania"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(TestFixtures.TENANT_ID.toString()))
                .andExpect(jsonPath("$.data.slug").value("tania"))
                .andExpect(jsonPath("$.data.razaoSocial").value("Tania Melo Advocacia"));
    }

    @Test
    @DisplayName("GET /resolve nunca expõe o nome do schema físico")
    void resolveDoesNotLeakTheSchemaName() throws Exception {
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(TestFixtures.tenant()));

        String body =
                mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "tania"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(-1, body.indexOf("tenant_tania"));
    }

    @Test
    @DisplayName("GET /resolve com slug desconhecido vira 404")
    void resolveOfUnknownSlugReturns404() throws Exception {
        when(tenantRepository.findBySlug("fantasma")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "fantasma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /resolve sem o parâmetro slug vira 400")
    void resolveWithoutSlugReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/resolve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
    }

    @Test
    @DisplayName("GET /current usa o tenant do contexto da requisição")
    void currentUsesTheRequestTenant() throws Exception {
        TenantContext.set("tenant_demo");
        when(tenantRepository.findBySchemaName("tenant_demo"))
                .thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(get("/api/v1/tenants/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("tania"))
                .andExpect(jsonPath("$.data.cnpj").value("12.345.678/0001-90"))
                .andExpect(jsonPath("$.data.plano").value("pro"))
                .andExpect(jsonPath("$.data.status").value("ativo"))
                .andExpect(jsonPath("$.data.responsavel").value("Tania Melo"));
    }

    @Test
    @DisplayName("GET /current sem tenant no contexto cai no schema default")
    void currentFallsBackToDefaultSchema() throws Exception {
        TenantContext.clear();
        when(tenantRepository.findBySchemaName("tenant_tania"))
                .thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(get("/api/v1/tenants/current")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /current com schema sem cadastro vira 404")
    void currentOfUnregisteredSchemaReturns404() throws Exception {
        when(tenantRepository.findBySchemaName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tenants/current")).andExpect(status().isNotFound());
    }
}
