package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JwtService: emissão e validação do access token")
class JwtServiceTest {

    private static final String SECRET = "uma-chave-de-desenvolvimento-com-mais-de-32-chars";
    private static final UUID USER_ID = UUID.randomUUID();

    private JwtService jwtService;

    /**
     * Fábrica dos testes de access token.
     *
     * <p>O construtor passou a receber também os minutos dos tokens de suporte (plataforma e sessão
     * impersonada). Nenhum teste daqui fala deles — o que se verifica aqui é emissão, validação e
     * expiração do access token — então eles ficam num valor fixo e fora de vista, em vez de
     * repetidos em cada `new JwtService(...)` dando a entender que importam.
     */
    private static JwtService comExpiracao(String secret, long minutos) {
        return new JwtService(secret, minutos, 60, 20);
    }

    @BeforeEach
    void setUp() {
        jwtService = comExpiracao(SECRET, 480);
    }

    @Test
    @DisplayName("token gerado carrega subject, uid, role e tenant")
    void generatedTokenCarriesAllClaims() {
        String token = jwtService.generateToken(USER_ID, "a@b.com", "ADMIN", "tenant-uuid");

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "JWT tem três segmentos");
        assertEquals("a@b.com", jwtService.extractEmail(token));
        assertEquals("tenant-uuid", jwtService.extractTenant(token));
        assertTrue(jwtService.isValid(token));
    }

    @Test
    @DisplayName("expiração é derivada dos minutos configurados")
    void expirationDerivesFromConfiguredMinutes() {
        assertEquals(480L * 60_000, jwtService.getExpirationMillis());
        assertEquals(60_000L, comExpiracao(SECRET, 1).getExpirationMillis());
    }

    @Test
    @DisplayName("segredo curto é preenchido para 256 bits em vez de derrubar a subida")
    void shortSecretIsPaddedInsteadOfFailing() {
        JwtService shortSecret = comExpiracao("curto", 10);
        String token = shortSecret.generateToken(USER_ID, "a@b.com", "STAFF", "t");
        assertTrue(shortSecret.isValid(token));
        assertEquals("a@b.com", shortSecret.extractEmail(token));
    }

    @Test
    @DisplayName("token assinado com outra chave é inválido")
    void tokenFromAnotherKeyIsRejected() {
        String foreign =
                comExpiracao("outra-chave-completamente-diferente-com-32-chars", 480)
                        .generateToken(USER_ID, "a@b.com", "ADMIN", "t");

        assertFalse(jwtService.isValid(foreign));
        assertThrows(JwtException.class, () -> jwtService.extractEmail(foreign));
    }

    @Test
    @DisplayName("token expirado é inválido")
    void expiredTokenIsInvalid() {
        JwtService expiring = comExpiracao(SECRET, 0);
        String token = expiring.generateToken(USER_ID, "a@b.com", "ADMIN", "t");
        assertFalse(expiring.isValid(token));
    }

    @ParameterizedTest(name = "\"{0}\" é inválido")
    @ValueSource(strings = {"", "   ", "nao-e-um-jwt", "a.b.c", "Bearer x.y.z"})
    void malformedTokensAreInvalid(String token) {
        assertFalse(jwtService.isValid(token));
    }

    @Test
    @DisplayName("token sem a claim tenant devolve null em extractTenant")
    void tokenWithoutTenantClaim() {
        String token = jwtService.generateToken(USER_ID, "a@b.com", "ADMIN", null);
        assertNull(jwtService.extractTenant(token));
    }

    @Test
    @DisplayName("dois tokens do mesmo usuário são ambos válidos")
    void multipleTokensRemainValid() {
        String first = jwtService.generateToken(USER_ID, "a@b.com", "ADMIN", "t1");
        String second = jwtService.generateToken(USER_ID, "a@b.com", "ADMIN", "t2");
        assertTrue(jwtService.isValid(first));
        assertTrue(jwtService.isValid(second));
        assertEquals("t1", jwtService.extractTenant(first));
        assertEquals("t2", jwtService.extractTenant(second));
    }

    // ── Tokens de suporte ──

    @Test
    @DisplayName("token de plataforma: escopo platform, uid do agente e nenhum tenant")
    void platformTokenHasNoTenant() {
        UUID agente = UUID.randomUUID();
        String token = jwtService.generatePlatformToken(agente, "agente@plataforma.dev");

        assertEquals("platform", jwtService.extractScope(token));
        assertEquals(agente, jwtService.extractUid(token));
        assertEquals("agente@plataforma.dev", jwtService.extractEmail(token));
        assertNull(jwtService.extractTenant(token), "sozinho não pode abrir dado de escritório");
        assertNull(jwtService.extractRole(token));
        assertNull(jwtService.extractActingSupportId(token));
        assertTrue(jwtService.isValid(token));
        assertEquals(60L * 60_000, jwtService.getSupportPlatformMillis());
    }

    @Test
    @DisplayName("sessão de suporte: ADMIN no tenant escolhido, com o agente real em act")
    void supportSessionCarriesActingAgent() {
        UUID agente = UUID.randomUUID();
        String token =
                jwtService.generateSupportSessionToken(agente, "agente@plataforma.dev", "tenant-x");

        assertEquals("support-session", jwtService.extractScope(token));
        assertEquals("ADMIN", jwtService.extractRole(token));
        assertEquals("tenant-x", jwtService.extractTenant(token));
        assertEquals(agente, jwtService.extractUid(token));
        assertEquals(agente, jwtService.extractActingSupportId(token));
        assertTrue(jwtService.isValid(token));
        assertEquals(20L * 60_000, jwtService.getSupportSessionMillis());
    }

    @Test
    @DisplayName("token normal de tenant não tem escopo nem ator de suporte")
    void tenantTokenHasNoScopeNorActor() {
        String token = jwtService.generateToken(USER_ID, "a@b.com", "STAFF", "tenant-uuid");

        assertNull(jwtService.extractScope(token));
        assertNull(jwtService.extractActingSupportId(token));
        assertEquals("STAFF", jwtService.extractRole(token));
        assertEquals(USER_ID, jwtService.extractUid(token));
    }
}
