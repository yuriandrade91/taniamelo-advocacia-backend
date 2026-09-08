package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantResolutionFilter: header X-Tenant-Id, depois a claim do JWT, depois o default")
class TenantResolutionFilterTest {

    @Mock private JwtService jwtService;
    @Mock private TenantRegistry tenantRegistry;

    private TenantResolutionFilter filter;
    private TenancyProperties properties;
    private MockHttpServletRequest request;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        properties = new TenancyProperties();
        properties.setHeaderName("X-Tenant-Id");
        properties.setDefaultSchema("tenant_tania");
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));
        filter = new TenantResolutionFilter(properties, jwtService, tenantRegistry);
        request = new MockHttpServletRequest();
        when(tenantRegistry.schemaFor(anyString())).thenReturn(Optional.empty());
        when(tenantRegistry.schemaFor(null)).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    /** Captura o tenant vigente DENTRO da cadeia, já que o filtro limpa o contexto no finally. */
    private String tenantSeenByTheChain() throws ServletException, IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(TenantContext.get());
        filter.doFilterInternal(request, response, chain);
        return seen.get();
    }

    @Test
    @DisplayName("header com slug conhecido define o schema")
    void headerWithKnownSlug() throws Exception {
        request.addHeader("X-Tenant-Id", "tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        assertEquals("tenant_tania", tenantSeenByTheChain());
        verify(jwtService, never()).extractTenant(anyString());
    }

    @Test
    @DisplayName("o header tem prioridade sobre a claim do JWT")
    void headerWinsOverJwtClaim() throws Exception {
        request.addHeader("X-Tenant-Id", "demo");
        request.addHeader("Authorization", "Bearer tok");
        when(tenantRegistry.schemaFor("demo")).thenReturn(Optional.of("tenant_demo"));

        assertEquals("tenant_demo", tenantSeenByTheChain());
        verify(jwtService, never()).extractTenant(anyString());
    }

    @Test
    @DisplayName("sem header, cai na claim tenant do JWT")
    void fallsBackToJwtClaim() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractTenant("tok")).thenReturn("uuid-do-tenant");
        when(tenantRegistry.schemaFor("uuid-do-tenant")).thenReturn(Optional.of("tenant_demo"));

        assertEquals("tenant_demo", tenantSeenByTheChain());
    }

    @Test
    @DisplayName("header desconhecido cai para a claim do JWT")
    void unknownHeaderFallsBackToJwt() throws Exception {
        request.addHeader("X-Tenant-Id", "nao-existe");
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractTenant("tok")).thenReturn("tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        assertEquals("tenant_tania", tenantSeenByTheChain());
    }

    @Test
    @DisplayName("sem header e sem token, nenhum tenant é definido (o resolver usa o default)")
    void noHeaderNoToken() throws Exception {
        assertNull(tenantSeenByTheChain());
    }

    @Test
    @DisplayName("token inválido não derruba a requisição - segue sem tenant")
    void invalidTokenIsSwallowed() throws Exception {
        request.addHeader("Authorization", "Bearer ruim");
        when(jwtService.extractTenant("ruim")).thenThrow(new IllegalArgumentException("inválido"));

        assertNull(tenantSeenByTheChain());
    }

    @Test
    @DisplayName("Authorization sem prefixo Bearer é ignorado")
    void nonBearerAuthorizationIsIgnored() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        assertNull(tenantSeenByTheChain());
        verify(jwtService, never()).extractTenant(anyString());
    }

    @Test
    @DisplayName("o contexto é sempre limpo ao fim da requisição")
    void contextIsAlwaysCleared() throws Exception {
        request.addHeader("X-Tenant-Id", "tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertNull(TenantContext.get(), "não pode vazar para a próxima requisição da mesma thread");
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("o contexto é limpo mesmo quando a cadeia estoura")
    void contextIsClearedOnException() {
        request.addHeader("X-Tenant-Id", "tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        FilterChain explodingChain =
                (req, res) -> {
                    throw new ServletException("boom");
                };

        org.junit.jupiter.api.Assertions.assertThrows(
                ServletException.class,
                () -> filter.doFilterInternal(request, response, explodingChain));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("o nome do header vem da configuração")
    void headerNameIsConfigurable() throws Exception {
        properties.setHeaderName("X-Escritorio");
        request.addHeader("X-Escritorio", "tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        assertEquals("tenant_tania", tenantSeenByTheChain());
    }

    @Test
    @DisplayName("assinatura do filtro aceita a request HTTP padrão")
    void acceptsStandardServletTypes() {
        assertNotNull((HttpServletRequest) request);
        assertNotNull((HttpServletResponse) response);
    }
}
