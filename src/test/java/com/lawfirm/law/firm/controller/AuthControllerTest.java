package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.AccountLockService;
import com.lawfirm.law.firm.security.JwtService;
import com.lawfirm.law.firm.security.LoginThrottleService;
import com.lawfirm.law.firm.service.RefreshTokenService;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import com.lawfirm.law.firm.tenant.TenantContext;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthController: login, refresh rotativo e logout")
class AuthControllerTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AccountLockService accountLock;

    /**
     * Real, não mock: o limite de tentativas é lógica de verdade e o teste de rajada abaixo precisa
     * dela funcionando. O bloqueio de conta é mock porque depende do banco.
     */
    private final LoginThrottleService loginThrottle = new LoginThrottleService();

    private MockMvc mockMvc;
    private TenancyProperties tenancyProperties;

    @BeforeEach
    void setUp() {
        tenancyProperties = new TenancyProperties();
        tenancyProperties.setDefaultSchema("tenant_tania");
        tenancyProperties.setSchemas(List.of("tenant_tania", "tenant_demo"));

        AuthController controller =
                new AuthController(
                        authenticationManager,
                        jwtService,
                        userRepository,
                        tenantRepository,
                        refreshTokenService,
                        tenancyProperties,
                        loginThrottle,
                        accountLock,
                        "refreshToken",
                        "/api/v1/auth",
                        false,
                        "Lax");

        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        when(jwtService.generateToken(any(), anyString(), anyString(), anyString()))
                .thenReturn("jwt-de-acesso");
        when(jwtService.getExpirationMillis()).thenReturn(480L * 60_000);
        when(refreshTokenService.getTtlSeconds()).thenReturn(1_209_600L);
        when(tenantRepository.findBySchemaName(anyString()))
                .thenReturn(Optional.of(TestFixtures.tenant()));
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(TestFixtures.user()));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("POST /login")
    class Login {

        @BeforeEach
        void stubIssue() {
            when(refreshTokenService.issue(any(), any()))
                    .thenReturn(
                            new RefreshTokenService.Issued(
                                    "refresh-em-claro", Instant.parse("2026-09-01T00:00:00Z")));
        }

        @Test
        @DisplayName("devolve o access token no corpo e o refresh em cookie httpOnly")
        void loginReturnsTokenAndCookie() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("User-Agent", "Mozilla/5.0")
                                    .content(
                                            "{\"login\":\"dra.tania@taniamelo.adv.br\",\"password\":\"password\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").value("jwt-de-acesso"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresInSeconds").value(28800))
                    .andExpect(jsonPath("$.data.fullName").value("Tania Melo"))
                    .andExpect(jsonPath("$.data.email").value("dra.tania@taniamelo.adv.br"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"))
                    .andExpect(jsonPath("$.data.tenantId").value(TestFixtures.TENANT_ID.toString()))
                    .andExpect(jsonPath("$.data.tenantSlug").value("tania"))
                    .andExpect(
                            header().string(
                                            "Set-Cookie",
                                            Matchers.containsString(
                                                    "refreshToken=refresh-em-claro")))
                    .andExpect(header().string("Set-Cookie", Matchers.containsString("HttpOnly")))
                    .andExpect(
                            header().string(
                                            "Set-Cookie",
                                            Matchers.containsString("Path=/api/v1/auth")))
                    .andExpect(
                            header().string("Set-Cookie", Matchers.containsString("SameSite=Lax")))
                    .andExpect(
                            header().string(
                                            "Set-Cookie",
                                            Matchers.containsString("Max-Age=1209600")));
        }

        @Test
        @DisplayName("o refresh token em claro nunca aparece no corpo da resposta")
        void refreshTokenIsNotInTheBody() throws Exception {
            String body =
                    mockMvc.perform(
                                    post("/api/v1/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"login\":\"a\",\"password\":\"b\"}"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            org.junit.jupiter.api.Assertions.assertEquals(-1, body.indexOf("refresh-em-claro"));
        }

        @Test
        @DisplayName("o user-agent da requisição é registrado com o refresh token")
        void userAgentIsRecorded() throws Exception {
            mockMvc.perform(
                    post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("User-Agent", "Chrome/1.0")
                            .content("{\"login\":\"a\",\"password\":\"b\"}"));

            verify(refreshTokenService).issue(TestFixtures.USER_ID, "Chrome/1.0");
        }

        @Test
        @DisplayName("aceita login por username, não só por e-mail")
        void acceptsUsernameLogin() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"dra.tania\",\"password\":\"password\"}"))
                    .andExpect(status().isOk());

            verify(userRepository)
                    .findByEmailIgnoreCaseOrUsernameIgnoreCase("dra.tania", "dra.tania");
        }

        @Test
        @DisplayName("credenciais inválidas viram 401 com mensagem neutra")
        void badCredentialsReturn401() throws Exception {
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("senha errada"));

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"a\",\"password\":\"errada\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.errors[0].message").value("Credenciais inválidas"));

            verify(refreshTokenService, never()).issue(any(), any());
        }

        @Test
        @DisplayName("login ou senha em branco viram 400 antes de autenticar")
        void blankCredentialsReturn400() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        @DisplayName("o token é emitido para o tenant resolvido na requisição")
        void tokenCarriesTheResolvedTenant() throws Exception {
            TenantContext.set("tenant_demo");

            mockMvc.perform(
                    post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"a\",\"password\":\"b\"}"));

            verify(tenantRepository).findBySchemaName("tenant_demo");
            verify(jwtService)
                    .generateToken(
                            TestFixtures.USER_ID,
                            "dra.tania@taniamelo.adv.br",
                            "ADMIN",
                            TestFixtures.TENANT_ID.toString());
        }

        @Test
        @DisplayName("sem tenant no contexto usa o schema default")
        void fallsBackToDefaultSchema() throws Exception {
            TenantContext.clear();

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"a\",\"password\":\"b\"}"))
                    .andExpect(status().isOk());

            verify(tenantRepository).findBySchemaName("tenant_tania");
        }

        @Test
        @DisplayName("tenant sem cadastro no catálogo vira 500 sem detalhe técnico")
        void unknownTenantReturns500() throws Exception {
            when(tenantRepository.findBySchemaName(anyString())).thenReturn(Optional.empty());

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"a\",\"password\":\"b\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errors[0].code").value("SYSTEM_ERROR"));
        }

        @Test
        @DisplayName("usuário autenticado mas ausente do banco vira 500 genérico")
        void authenticatedUserMissingFromDatabase() throws Exception {
            when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"a\",\"password\":\"b\"}"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @BeforeEach
        void stubRotation() {
            when(refreshTokenService.rotate(any(), any()))
                    .thenReturn(
                            new RefreshTokenService.Rotation(
                                    TestFixtures.USER_ID,
                                    "novo-refresh",
                                    Instant.parse("2026-09-15T00:00:00Z")));
            when(userRepository.findById(TestFixtures.USER_ID))
                    .thenReturn(Optional.of(TestFixtures.user()));
        }

        @Test
        @DisplayName("lê o cookie, rotaciona e devolve um novo access token")
        void rotatesAndReturnsNewAccessToken() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .cookie(new Cookie("refreshToken", "refresh-antigo"))
                                    .header("User-Agent", "Firefox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("jwt-de-acesso"))
                    .andExpect(jsonPath("$.data.tenantSlug").value("tania"))
                    .andExpect(
                            header().string(
                                            "Set-Cookie",
                                            Matchers.containsString("refreshToken=novo-refresh")));

            verify(refreshTokenService).rotate("refresh-antigo", "Firefox");
        }

        @Test
        @DisplayName("sem cookie, o service recebe null e recusa")
        void withoutCookieTheServiceGetsNull() throws Exception {
            when(refreshTokenService.rotate(any(), any()))
                    .thenThrow(new BadCredentialsException("Refresh token ausente"));

            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDENTIALS"));

            verify(refreshTokenService).rotate(null, null);
        }

        @Test
        @DisplayName("outros cookies na requisição são ignorados")
        void otherCookiesAreIgnored() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .cookie(
                                            new Cookie("theme", "dark"),
                                            new Cookie("refreshToken", "o-certo"),
                                            new Cookie("locale", "pt-BR")))
                    .andExpect(status().isOk());

            verify(refreshTokenService).rotate("o-certo", null);
        }

        @Test
        @DisplayName("token revogado ou expirado vira 401")
        void revokedTokenReturns401() throws Exception {
            when(refreshTokenService.rotate(any(), any()))
                    .thenThrow(
                            new BadCredentialsException("Refresh token expirado ou já utilizado"));

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .cookie(new Cookie("refreshToken", "roubado")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("usuário do token não encontrado vira 401")
        void missingUserReturns401() throws Exception {
            when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.empty());

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .cookie(new Cookie("refreshToken", "valido")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("revoga o refresh token e limpa o cookie")
        void revokesAndClearsTheCookie() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/logout")
                                    .cookie(new Cookie("refreshToken", "o-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value("Logout efetuado."))
                    .andExpect(
                            header().string("Set-Cookie", Matchers.containsString("refreshToken=")))
                    .andExpect(header().string("Set-Cookie", Matchers.containsString("Max-Age=0")))
                    .andExpect(header().string("Set-Cookie", Matchers.containsString("HttpOnly")));

            verify(refreshTokenService).revoke("o-token");
        }

        @Test
        @DisplayName("logout sem cookie ainda responde 200 (idempotente)")
        void logoutWithoutCookieIsIdempotent() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());

            verify(refreshTokenService).revoke(null);
        }
    }

    @Test
    @DisplayName("rajada de senhas erradas passa a devolver 429 com Retry-After")
    void bruteForceIsThrottled() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("credenciais inválidas"));

        // As cinco primeiras respondem 401: é o comportamento normal de senha errada.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"dra.tania\",\"password\":\"errada\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // A sexta nem chega a verificar a senha - que é o ponto: um ataque quer justamente
        // forçar o custo de hashing a cada tentativa.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"dra.tania\",\"password\":\"errada\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("a resposta do limite não distingue conta bloqueada de IP throttled")
    void throttleResponseDoesNotLeakAccountExistence() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("credenciais inválidas"));

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(
                    post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"existe-ou-nao\",\"password\":\"x\"}"));
        }

        // Mensagem genérica: se ela dissesse "conta bloqueada", bastaria errar a senha seis
        // vezes para descobrir que aquele login existe.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"existe-ou-nao\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value("Muitas tentativas. Tente novamente em alguns minutos."));
    }

    @Test
    @DisplayName("cookie seguro/SameSite=None é aplicado quando configurado (produção)")
    void secureCookieConfiguration() throws Exception {
        AuthController secureController =
                new AuthController(
                        authenticationManager,
                        jwtService,
                        userRepository,
                        tenantRepository,
                        refreshTokenService,
                        tenancyProperties,
                        loginThrottle,
                        accountLock,
                        "rt",
                        "/api/v1/auth",
                        true,
                        "None");
        when(refreshTokenService.issue(any(), any()))
                .thenReturn(new RefreshTokenService.Issued("t", Instant.now()));

        MockMvc secureMvc =
                MockMvcBuilders.standaloneSetup(secureController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        secureMvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", Matchers.containsString("rt=t")))
                .andExpect(header().string("Set-Cookie", Matchers.containsString("Secure")))
                .andExpect(header().string("Set-Cookie", Matchers.containsString("SameSite=None")));
    }

    @Test
    @DisplayName("o nome do cookie é configurável e o lookup respeita isso")
    void cookieNameIsConfigurable() throws Exception {
        AuthController custom =
                new AuthController(
                        authenticationManager,
                        jwtService,
                        userRepository,
                        tenantRepository,
                        refreshTokenService,
                        tenancyProperties,
                        loginThrottle,
                        accountLock,
                        "meu_refresh",
                        "/api/v1/auth",
                        false,
                        "Lax");
        MockMvc customMvc = MockMvcBuilders.standaloneSetup(custom).build();

        customMvc.perform(
                post("/api/v1/auth/logout")
                        .cookie(
                                new Cookie("refreshToken", "ignorado"),
                                new Cookie("meu_refresh", "certo")));

        verify(refreshTokenService).revoke("certo");
    }

    @Test
    @DisplayName("o usuário devolvido no corpo é o que veio do repositório")
    void bodyReflectsTheStoredUser() throws Exception {
        User staff = TestFixtures.user();
        staff.setFullName("Ana Souza");
        staff.setEmail("ana.souza@taniamelo.adv.br");
        staff.setRole(com.lawfirm.law.firm.model.Role.valueOf(staff.getRole().name()));
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(staff));
        when(refreshTokenService.issue(any(), any()))
                .thenReturn(new RefreshTokenService.Issued("t", Instant.now()));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"ana.souza\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Ana Souza"))
                .andExpect(jsonPath("$.data.email").value("ana.souza@taniamelo.adv.br"));
    }
}
