package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
@DisplayName("TenantResolutionFilter: header X-Tenant-Id, depois a claim do JWT, senão 400")
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
    @DisplayName("header desconhecido é RECUSADO com 400, sem chegar na cadeia")
    void unknownHeaderIsRejected() throws Exception {
        request.addHeader("X-Tenant-Id", "nao-existe");

        AtomicReference<Boolean> chegou = new AtomicReference<>(false);
        filter.doFilterInternal(request, response, (req, res) -> chegou.set(true));

        // Antes, o header desconhecido era ignorado e a requisição seguia para o
        // schema default - que era o escritório real. Um erro de digitação no
        // X-Tenant-Id autenticava na base errada, em silêncio.
        assertEquals(400, response.getStatus());
        assertFalse(chegou.get(), "a requisição não pode seguir com tenant inválido");
        assertTrue(response.getContentAsString().contains("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("header desconhecido é recusado mesmo com JWT válido de outro escritório")
    void unknownHeaderIsRejectedEvenWithValidJwt() throws Exception {
        request.addHeader("X-Tenant-Id", "nao-existe");
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractTenant("tok")).thenReturn("tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        // Cair na claim aqui reinterpretaria em silêncio uma instrução explícita e
        // errada de quem chamou.
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(400, response.getStatus());
    }

    @Test
    @DisplayName("header em branco é tratado como ausente, não como inválido")
    void blankHeaderIsTreatedAsAbsent() throws Exception {
        request.addHeader("X-Tenant-Id", "   ");
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractTenant("tok")).thenReturn("tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        // Cliente que manda o header vazio não escolheu escritório nenhum; recusar
        // aqui quebraria quem só esquece de remover o cabeçalho.
        assertEquals("tenant_tania", tenantSeenByTheChain());
    }

    @Test
    @DisplayName("a mensagem de recusa não revela quais escritórios existem")
    void rejectionDoesNotLeakTenantNames() throws Exception {
        request.addHeader("X-Tenant-Id", "chute");
        filter.doFilterInternal(request, response, new MockFilterChain());

        String corpo = response.getContentAsString();
        assertFalse(corpo.contains("tenant_tania"), "vazou o nome do schema");
        assertFalse(corpo.contains("tenant_demo"), "vazou o nome do schema");
    }

    @Test
    @DisplayName("fora de /api/v1, sem header e sem token, segue sem tenant (actuator, Swagger)")
    void noHeaderNoTokenOutsideTheApi() throws Exception {
        request.setRequestURI("/actuator/health");

        assertNull(tenantSeenByTheChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("rota de escritório sem header e sem token é RECUSADA com 400 - não há default")
    void officeRouteWithoutTenantIsRejected() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/auth/login");

        AtomicReference<Boolean> chegou = new AtomicReference<>(false);
        filter.doFilterInternal(request, response, (req, res) -> chegou.set(true));

        // Com o default, este login autenticava contra os usuários da Tania.
        assertEquals(400, response.getStatus());
        assertFalse(chegou.get(), "a requisição não pode seguir sem tenant");
        assertTrue(response.getContentAsString().contains("TENANT_REQUIRED"));
    }

    @Test
    @DisplayName("rotas de plataforma e a resolução por slug não exigem tenant")
    void platformRoutesDoNotRequireTenant() throws Exception {
        for (String uri : new String[] {"/api/v1/support/login", "/api/v1/tenants/resolve"}) {
            request.setRequestURI(uri);
            AtomicReference<Boolean> chegou = new AtomicReference<>(false);
            filter.doFilterInternal(request, response, (req, res) -> chegou.set(true));
            assertTrue(chegou.get(), uri);
        }
    }

    @Test
    @DisplayName("preflight OPTIONS não exige tenant - o navegador não manda cabeçalho próprio")
    void preflightDoesNotRequireTenant() throws Exception {
        request.setMethod("OPTIONS");
        request.setRequestURI("/api/v1/clients");

        assertNull(tenantSeenByTheChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("rota de escritório com Bearer sem tenant segue para a autenticação dar 401")
    void bearerWithoutTenantReachesAuthentication() throws Exception {
        request.setRequestURI("/api/v1/clients");
        request.addHeader("Authorization", "Bearer expirado");
        when(jwtService.extractTenant("expirado")).thenThrow(new IllegalArgumentException("exp"));

        // O 401 é o que faz o front tentar o refresh; um 400 aqui quebraria esse fluxo.
        assertNull(tenantSeenByTheChain());
        assertEquals(200, response.getStatus());
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
