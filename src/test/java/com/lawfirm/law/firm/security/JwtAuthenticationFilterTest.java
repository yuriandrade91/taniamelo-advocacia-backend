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
import org.junit.jupiter.api.AfterEach;
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

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;

    @InjectMocks private JwtAuthenticationFilter filter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("token válido popula o SecurityContext com o principal do usuário")
    void validTokenAuthenticates() throws Exception {
        User user = TestFixtures.user();
        request.addHeader("Authorization", "Bearer tok123");
        when(jwtService.isValid("tok123")).thenReturn(true);
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

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertEquals(existing, SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(user.getEmail());
    }
}
