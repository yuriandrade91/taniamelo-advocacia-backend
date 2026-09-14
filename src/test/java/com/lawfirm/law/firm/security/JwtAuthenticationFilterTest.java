package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenantContext;
import com.lawfirm.law.firm.tenant.TenantRegistry;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter: autentica a requisição a partir do Bearer token")
class JwtAuthenticationFilterTest {

    private static final String SCHEMA = "tenant_demo";
    private static final String PUBLICO = "demo";

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private TenantRegistry tenantRegistry;

    @InjectMocks private JwtAuthenticationFilter filter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /**
     * A requisição já chega com o escritório resolvido: o TenantResolutionFilter roda antes deste
     * (SecurityConfig: addFilterBefore(tenantResolutionFilter, JwtAuthenticationFilter.class)).
     */
    @BeforeEach
    void escritorioDaRequisicao() {
        TenantContext.set(SCHEMA);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    /** Atalho para o caso comum: o token foi emitido para o escritório da requisição. */
    private void tokenDoMesmoEscritorio(String token) {
        when(jwtService.extractTenant(token)).thenReturn(PUBLICO);
        when(tenantRegistry.schemaFor(PUBLICO)).thenReturn(Optional.of(SCHEMA));
    }

    @Test
    @DisplayName("token válido popula o SecurityContext com o principal do usuário")
    void validTokenAuthenticates() throws Exception {
        User user = TestFixtures.user();
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        tokenDoMesmoEscritorio("tok123");
        when(jwtService.extractEmail("tok123")).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail()))
                .thenReturn(new UserPrincipal(user));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(user.getEmail(), ((UserPrincipal) auth.getPrincipal()).getUsername());
        assertNotNull(auth.getDetails(), "detalhes web são anexados para auditoria");
        assertNotNull(chain.getRequest(), "a cadeia continua");
    }

    @Test
    @DisplayName("sem header Authorization a requisição segue anônima")
    void noHeaderLeavesContextEmpty() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).isValid(anyString());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("header sem o prefixo Bearer é ignorado")
    void nonBearerHeaderIsIgnored() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).isValid(anyString());
    }

    @Test
    @DisplayName("token inválido não autentica, mas deixa a requisição seguir para o 401")
    void invalidTokenDoesNotAuthenticate() throws Exception {
        request.addHeader("Authorization", "Bearer ruim");
        when(jwtService.isValid("ruim")).thenReturn(false);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("usuário inativo não é autenticado mesmo com token válido")
    void inactiveUserIsNotAuthenticated() throws Exception {
        User user = TestFixtures.user();
        user.setActive(false);
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        tokenDoMesmoEscritorio("tok123");
        when(jwtService.extractEmail("tok123")).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail()))
                .thenReturn(new UserPrincipal(user));

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("exceção ao carregar o usuário limpa o contexto sem derrubar a requisição")
    void exceptionClearsContextAndContinues() throws Exception {
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        tokenDoMesmoEscritorio("tok123");
        when(jwtService.extractEmail("tok123")).thenReturn("some@one.com");
        when(userDetailsService.loadUserByUsername("some@one.com"))
                .thenThrow(new UsernameNotFoundException("sumiu"));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("contexto já autenticado não é sobrescrito")
    void existingAuthenticationIsPreserved() throws Exception {
        User user = TestFixtures.user();
        var existing =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "ja-autenticado", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        tokenDoMesmoEscritorio("tok123");

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(existing, SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(user.getEmail());
    }

    /* ────────────────────────────────────────────────────────────────
     * O token pertence ao escritório que o emitiu.
     *
     * O vetor que faltava: o TenantResolutionFilter resolve o escritório pelo
     * X-Tenant-Id ANTES deste filtro, e o usuário é carregado pelo e-mail no
     * schema que o cabeçalho escolheu. Como o AdminUserSeeder semeia o mesmo
     * e-mail e a mesma senha em todos os schemas, existia uma conta presente em
     * todos os escritórios - e com ela, token de um + cabeçalho de outro lia a
     * carteira alheia, criava compromisso nela e abria a senha do INSS dos
     * clientes dela.
     *
     * O teste de isolamento que já existia compara escritórios usando tokens
     * DIFERENTES; nunca token de um com cabeçalho do outro.
     * ──────────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("token de outro escritório NÃO autentica, mesmo sendo válido e de usuário ativo")
    void tokenDeOutroEscritorioNaoAutentica() throws Exception {
        User user = TestFixtures.user();
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        // Token emitido para "tania"; a requisição chegou resolvida como tenant_demo.
        when(jwtService.extractTenant("tok123")).thenReturn("tania");
        when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        // E nem chega a procurar o usuário: a recusa é antes do banco do outro escritório.
        verify(userDetailsService, never()).loadUserByUsername(user.getEmail());
    }

    @Test
    @DisplayName("slug e UUID do MESMO escritório são o mesmo lugar")
    void slugEUuidDoMesmoEscritorioSaoAceitos() throws Exception {
        // A comparação é entre SCHEMAS resolvidos, não entre os textos: o
        // identificador público aceita slug ou UUID, e comparar string recusaria
        // um token emitido com uma das formas e um cabeçalho com a outra.
        User user = TestFixtures.user();
        String uuidDoEscritorio = "18a92b50-63c5-42cc-ac69-2bf9f1ee824d";
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        when(jwtService.extractTenant("tok123")).thenReturn(uuidDoEscritorio);
        when(tenantRegistry.schemaFor(uuidDoEscritorio)).thenReturn(Optional.of(SCHEMA));
        when(jwtService.extractEmail("tok123")).thenReturn(user.getEmail());
        when(userDetailsService.loadUserByUsername(user.getEmail()))
                .thenReturn(new UserPrincipal(user));

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("token sem claim de escritório não autentica")
    void tokenSemClaimDeEscritorioNaoAutentica() throws Exception {
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
        when(jwtService.extractTenant("tok123")).thenReturn(null);
        when(tenantRegistry.schemaFor(null)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("requisição sem escritório resolvido não autentica")
    void semEscritorioResolvidoNaoAutentica() throws Exception {
        // Não deveria acontecer (o TenantResolutionFilter recusa antes), mas se
        // acontecer a resposta é recusar, não assumir um escritório qualquer.
        TenantContext.clear();
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
