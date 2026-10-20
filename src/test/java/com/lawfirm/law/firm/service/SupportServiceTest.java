package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.SupportSessionResponseDTO;
import com.lawfirm.law.firm.dto.SupportTokenResponseDTO;
import com.lawfirm.law.firm.exception.BusinessException;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.SupportUser;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.SupportUserRepository;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.security.JwtService;
import com.lawfirm.law.firm.security.LoginThrottleService;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenantRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SupportService: login do agente e abertura de sessão num tenant")
class SupportServiceTest {

    private static final UUID AGENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String EMAIL = "agente@plataforma.dev";
    private static final String IP = "10.0.0.7";

    @Mock private SupportUserRepository supportUserRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRegistry tenantRegistry;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private LoginThrottleService loginThrottle;

    private SupportService service;
    private SupportUser agent;

    @BeforeEach
    void setUp() {
        service =
                new SupportService(
                        supportUserRepository,
                        tenantRepository,
                        tenantRegistry,
                        passwordEncoder,
                        jwtService,
                        loginThrottle);

        agent = TestFixtures.withId(new SupportUser(), AGENT_ID);
        agent.setFullName("Agente de Suporte");
        agent.setEmail(EMAIL);
        agent.setPasswordHash("hash");
        agent.setActive(true);

        when(jwtService.getSupportPlatformMillis()).thenReturn(60L * 60_000);
        when(jwtService.getSupportSessionMillis()).thenReturn(20L * 60_000);
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("credencial certa devolve token de plataforma e zera a cota")
        void validCredentialsIssuePlatformToken() {
            when(supportUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(agent));
            when(passwordEncoder.matches("segredo", "hash")).thenReturn(true);
            when(jwtService.generatePlatformToken(AGENT_ID, EMAIL)).thenReturn("token-plataforma");

            SupportTokenResponseDTO dto = service.login(EMAIL, "segredo", IP);

            assertEquals("token-plataforma", dto.token());
            assertEquals("Bearer", dto.tokenType());
            assertEquals(3600, dto.expiresInSeconds());
            assertEquals("Agente de Suporte", dto.name());
            verify(loginThrottle).ensureAllowed(IP, EMAIL);
            verify(loginThrottle).registerSuccess(EMAIL);
            verify(loginThrottle, never()).registerFailure(anyString(), anyString());
        }

        @Test
        @DisplayName("senha errada consome cota e responde como credencial inválida")
        void wrongPasswordCountsAsFailure() {
            when(supportUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(agent));
            when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

            assertThrows(BadCredentialsException.class, () -> service.login(EMAIL, "errada", IP));
            verify(loginThrottle).registerFailure(IP, EMAIL);
            verify(jwtService, never()).generatePlatformToken(any(), anyString());
        }

        @Test
        @DisplayName("agente inexistente tem a mesma resposta de senha errada")
        void unknownAgentLooksLikeWrongPassword() {
            when(supportUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

            BadCredentialsException ex =
                    assertThrows(
                            BadCredentialsException.class, () -> service.login(EMAIL, "x", IP));
            assertEquals("Credenciais inválidas", ex.getMessage());
            verify(loginThrottle).registerFailure(IP, EMAIL);
        }

        @Test
        @DisplayName("agente desativado não entra, mesmo com a senha certa")
        void inactiveAgentIsRefused() {
            agent.setActive(false);
            when(supportUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(agent));
            when(passwordEncoder.matches("segredo", "hash")).thenReturn(true);

            assertThrows(BadCredentialsException.class, () -> service.login(EMAIL, "segredo", IP));
            verify(loginThrottle).registerFailure(IP, EMAIL);
        }
    }

    @Nested
    @DisplayName("openSession")
    class OpenSession {

        @BeforeEach
        void agenteAtivo() {
            when(supportUserRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        }

        @Test
        @DisplayName("tenant ativo devolve token curto de impersonation com os dados públicos")
        void activeTenantOpensSession() {
            Tenant tenant = TestFixtures.tenant();
            when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));
            when(tenantRepository.findBySchemaName("tenant_tania")).thenReturn(Optional.of(tenant));
            when(jwtService.generateSupportSessionToken(
                            AGENT_ID, EMAIL, TestFixtures.TENANT_ID.toString()))
                    .thenReturn("token-sessao");

            SupportSessionResponseDTO dto = service.openSession(AGENT_ID, "tania");

            assertEquals("token-sessao", dto.token());
            assertEquals(1200, dto.expiresInSeconds());
            assertEquals(TestFixtures.TENANT_ID.toString(), dto.tenantId());
            assertEquals("tania", dto.tenantSlug());
            assertEquals("Tania Melo Advocacia", dto.tenantRazaoSocial());
        }

        @Test
        @DisplayName("agente desativado depois do login perde a sessão")
        void inactiveAgentCannotOpenSession() {
            agent.setActive(false);

            assertThrows(
                    BadCredentialsException.class, () -> service.openSession(AGENT_ID, "tania"));
        }

        @Test
        @DisplayName("identificador que não é tenant conhecido é 404")
        void unknownTenantIsNotFound() {
            when(tenantRegistry.schemaFor("nada")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.openSession(AGENT_ID, "nada"));
        }

        @Test
        @DisplayName("schema sem registro no catálogo também é 404")
        void schemaWithoutCatalogEntryIsNotFound() {
            when(tenantRegistry.schemaFor("orfao")).thenReturn(Optional.of("tenant_orfao"));
            when(tenantRepository.findBySchemaName("tenant_orfao")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.openSession(AGENT_ID, "orfao"));
        }

        @Test
        @DisplayName("escritório inativo recusa a sessão de suporte")
        void inactiveTenantIsRefused() {
            Tenant inativo = TestFixtures.tenant();
            inativo.setStatus("suspenso");
            when(tenantRegistry.schemaFor("tania")).thenReturn(Optional.of("tenant_tania"));
            when(tenantRepository.findBySchemaName("tenant_tania"))
                    .thenReturn(Optional.of(inativo));

            assertThrows(BusinessException.class, () -> service.openSession(AGENT_ID, "tania"));
            verify(jwtService, never())
                    .generateSupportSessionToken(any(), anyString(), anyString());
        }
    }
}
