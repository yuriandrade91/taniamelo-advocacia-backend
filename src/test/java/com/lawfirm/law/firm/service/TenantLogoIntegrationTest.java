package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.tenant.TenantContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * A logo com a cadeia inteira: filtro de tenant, regras de segurança, banco e storage local. É o
 * que o teste do controller não alcança - que um {@code <img>}, sem token e sem X-Tenant-Id, recebe
 * a imagem, e que só ADMIN troca a marca.
 */
@DisplayName("Logo do escritório: papel exigido, rota pública e storage de verdade")
class TenantLogoIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7, 7};

    @Autowired private WebApplicationContext context;
    @Autowired private TenantService tenantService;
    @Autowired private TenantRepository tenantRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A linha do escritório é de toda a suíte: ninguém depois deste teste herda a logo. */
    @AfterEach
    void backToTheDefaultBrand() {
        TenantContext.set("tenant_tania");
        tenantService.removeLogo();
    }

    private ResultActions upload(String role) throws Exception {
        return mockMvc.perform(
                multipart(HttpMethod.PUT, "/api/v1/tenants/current/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .header("X-Tenant-Id", "tania")
                        .with(user("gestao@escritorio.test").roles(role)));
    }

    @Test
    @DisplayName("ADMIN envia e a imagem sai na rota pública, sem token e sem X-Tenant-Id")
    void adminUploadsAndTheImageIsPublic() throws Exception {
        String body =
                upload("ADMIN")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String logoUrl = JsonPath.read(body, "$.data.logoUrl");

        mockMvc.perform(get(logoUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", Matchers.containsString("immutable")));
        mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "tania"))
                .andExpect(jsonPath("$.data.logoUrl").value(logoUrl));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "SUPPORT"})
    @DisplayName("quem não é ADMIN não troca a marca do escritório")
    void onlyAdminChangesTheLogo(String role) throws Exception {
        upload(role).andExpect(status().isForbidden());

        TenantContext.set("tenant_tania");
        assertNull(tenantRepository.findBySlug("tania").orElseThrow().getLogoKey());
    }

    @Test
    @DisplayName("sem autenticação não envia")
    void anonymousCannotUpload() throws Exception {
        mockMvc.perform(
                        multipart(HttpMethod.PUT, "/api/v1/tenants/current/logo")
                                .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                                .header("X-Tenant-Id", "tania"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN volta à marca padrão: a rota pública passa a 404 e o resolve, a nulo")
    void adminGoesBackToTheDefaultBrand() throws Exception {
        upload("ADMIN").andExpect(status().isOk());

        mockMvc.perform(
                        delete("/api/v1/tenants/current/logo")
                                .header("X-Tenant-Id", "tania")
                                .with(user("gestao@escritorio.test").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tenants/logo").param("slug", "tania"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "tania"))
                .andExpect(jsonPath("$.data.logoUrl").doesNotExist());
    }
}
