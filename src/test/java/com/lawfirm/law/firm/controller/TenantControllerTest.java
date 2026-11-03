package com.lawfirm.law.firm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.storage.FileStorageService;
import com.lawfirm.law.firm.storage.LoadedFile;
import com.lawfirm.law.firm.storage.StoredFile;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenantContext;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantController: resolução pública por slug e tenant corrente")
class TenantControllerTest {

    /** Os oito bytes de assinatura bastam: o servidor confere o tipo pelo começo do arquivo. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    @Mock private TenantRepository tenantRepository;
    @Mock private FileStorageService fileStorageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new TenantController(
                                        new com.lawfirm.law.firm.service.TenantService(
                                                tenantRepository, fileStorageService)))
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
    @DisplayName("GET /current sem tenant no contexto vira 404, sem cair em escritório padrão")
    void currentWithoutTenantReturns404() throws Exception {
        TenantContext.clear();
        when(tenantRepository.findBySchemaName("tenant_tania"))
                .thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(get("/api/v1/tenants/current")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /current com schema sem cadastro vira 404")
    void currentOfUnregisteredSchemaReturns404() throws Exception {
        when(tenantRepository.findBySchemaName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tenants/current")).andExpect(status().isNotFound());
    }

    // ── Logo do escritório ──

    private static Tenant tenantWithLogo() {
        Tenant tenant = TestFixtures.tenant();
        tenant.useLogo("tenant_tania/office/antiga.png", "image/png");
        return tenant;
    }

    private static String versionOf(Tenant tenant) {
        return String.valueOf(tenant.getLogoUpdatedAt().toEpochMilli());
    }

    private static MockMultipartFile logoFile(byte[] content, String declaredType) {
        return new MockMultipartFile("file", "logo.png", declaredType, content);
    }

    private org.springframework.test.web.servlet.ResultActions upload(MockMultipartFile file)
            throws Exception {
        return mockMvc.perform(
                multipart(HttpMethod.PUT, "/api/v1/tenants/current/logo").file(file));
    }

    @Test
    @DisplayName(
            "sem logo enviada, resolve e current dizem logoUrl nulo: o front usa a marca padrão")
    void withoutLogoTheUrlIsNull() throws Exception {
        TenantContext.set("tenant_tania");
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(TestFixtures.tenant()));
        when(tenantRepository.findBySchemaName("tenant_tania"))
                .thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "tania"))
                .andExpect(jsonPath("$.data.logoUrl").doesNotExist());
        mockMvc.perform(get("/api/v1/tenants/current"))
                .andExpect(jsonPath("$.data.logoUrl").doesNotExist());
    }

    @Test
    @DisplayName("com logo, resolve devolve a URL pública com a versão")
    void resolveReturnsTheVersionedLogoUrl() throws Exception {
        Tenant tenant = tenantWithLogo();
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get("/api/v1/tenants/resolve").param("slug", "tania"))
                .andExpect(
                        jsonPath("$.data.logoUrl")
                                .value("/api/v1/tenants/logo?slug=tania&v=" + versionOf(tenant)));
    }

    @Test
    @DisplayName("GET /logo com a versão atual serve a imagem com cache longo e o tipo conferido")
    void logoWithCurrentVersionIsCachedForLong() throws Exception {
        Tenant tenant = tenantWithLogo();
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(tenant));
        when(fileStorageService.load("tenant_tania/office/antiga.png"))
                .thenReturn(new LoadedFile(new ByteArrayResource(PNG), "application/octet-stream"));

        mockMvc.perform(
                        get("/api/v1/tenants/logo")
                                .param("slug", "tania")
                                .param("v", versionOf(tenant)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    @DisplayName("GET /logo sem a versão, ou com uma antiga, não fica em cache")
    void logoWithoutVersionIsRevalidated() throws Exception {
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(tenantWithLogo()));
        when(fileStorageService.load(anyString()))
                .thenReturn(new LoadedFile(new ByteArrayResource(PNG), "image/png"));

        mockMvc.perform(get("/api/v1/tenants/logo").param("slug", "tania").param("v", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    @DisplayName("GET /logo de quem usa a marca padrão vira 404, sem tocar no storage")
    void logoOfDefaultBrandIsNotFound() throws Exception {
        when(tenantRepository.findBySlug("tania")).thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(get("/api/v1/tenants/logo").param("slug", "tania"))
                .andExpect(status().isNotFound());
        verify(fileStorageService, never()).load(anyString());
    }

    @Test
    @DisplayName("PUT /current/logo grava na pasta do escritório, troca a URL e apaga a anterior")
    void uploadStoresInTheTenantFolderAndDropsThePrevious() throws Exception {
        TenantContext.set("tenant_tania");
        Tenant tenant = tenantWithLogo();
        when(tenantRepository.findBySchemaName("tenant_tania")).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(call -> call.getArgument(0));
        when(fileStorageService.store(any(), eq("tenant_tania/office")))
                .thenReturn(new StoredFile("tenant_tania/office/nova.png", "image/png", 10));

        upload(logoFile(PNG, "image/png"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.logoUrl")
                                .value(startsWith("/api/v1/tenants/logo?slug=tania&v=")));

        org.junit.jupiter.api.Assertions.assertEquals(
                "tenant_tania/office/nova.png", tenant.getLogoKey());
        verify(fileStorageService).delete("tenant_tania/office/antiga.png");
    }

    @Test
    @DisplayName("o tipo vem dos bytes: PDF com nome e Content-Type de PNG é recusado")
    void uploadChecksTheBytesNotTheDeclaredType() throws Exception {
        TenantContext.set("tenant_tania");

        upload(logoFile("%PDF-1.4 falso".getBytes(), "image/png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM_VALUE"));
        verify(fileStorageService, never()).store(any(), anyString());
    }

    @Test
    @DisplayName("logo acima de 2 MB vira FILE_TOO_LARGE, o mesmo código do limite global")
    void uploadAboveTwoMegabytesIsRefused() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        upload(logoFile(big, "image/png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("FILE_TOO_LARGE"));
        verify(fileStorageService, never()).store(any(), anyString());
    }

    @Test
    @DisplayName("se a linha não salva, a imagem nova sai do storage e a antiga fica")
    void failedSaveRemovesTheNewFile() throws Exception {
        TenantContext.set("tenant_tania");
        when(tenantRepository.findBySchemaName("tenant_tania"))
                .thenReturn(Optional.of(tenantWithLogo()));
        when(tenantRepository.save(any(Tenant.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fora"));
        when(fileStorageService.store(any(), anyString()))
                .thenReturn(new StoredFile("tenant_tania/office/nova.png", "image/png", 10));

        upload(logoFile(PNG, "image/png")).andExpect(status().isInternalServerError());

        verify(fileStorageService).delete("tenant_tania/office/nova.png");
        verify(fileStorageService, never()).delete("tenant_tania/office/antiga.png");
    }

    @Test
    @DisplayName("DELETE /current/logo volta à marca padrão e apaga o arquivo")
    void removeGoesBackToTheDefaultBrand() throws Exception {
        TenantContext.set("tenant_tania");
        Tenant tenant = tenantWithLogo();
        when(tenantRepository.findBySchemaName("tenant_tania")).thenReturn(Optional.of(tenant));

        mockMvc.perform(delete("/api/v1/tenants/current/logo")).andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertNull(tenant.getLogoKey());
        verify(tenantRepository).save(tenant);
        verify(fileStorageService).delete("tenant_tania/office/antiga.png");
    }

    @Test
    @DisplayName("DELETE sem logo enviada é idempotente: 204 sem gravar nada")
    void removeWithoutLogoDoesNothing() throws Exception {
        TenantContext.set("tenant_tania");
        when(tenantRepository.findBySchemaName("tenant_tania"))
                .thenReturn(Optional.of(TestFixtures.tenant()));

        mockMvc.perform(delete("/api/v1/tenants/current/logo")).andExpect(status().isNoContent());

        verify(tenantRepository, never()).save(any());
        verify(fileStorageService, never()).delete(anyString());
    }
}
