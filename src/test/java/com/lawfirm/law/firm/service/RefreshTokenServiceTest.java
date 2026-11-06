package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.RefreshToken;
import com.lawfirm.law.firm.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RefreshTokenService: emissão, rotação e defesa contra reuso")
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, 14);
        when(repository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
    }

    private RefreshToken captureSaved() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private RefreshToken activeToken() {
        RefreshToken token = new RefreshToken();
        token.setUserId(USER_ID);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        return token;
    }

    @Test
    @DisplayName("issue devolve o token em claro e grava só o hash")
    void issueStoresOnlyTheHash() {
        RefreshTokenService.Issued issued = service.issue(USER_ID, "Mozilla/5.0");

        assertNotNull(issued.rawToken());
        assertTrue(issued.rawToken().length() >= 42, "32 bytes em base64url");
        assertTrue(issued.expiresAt().isAfter(Instant.now()));

        RefreshToken saved = captureSaved();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(64, saved.getTokenHash().length(), "SHA-256 em hex");
        assertTrue(saved.getTokenHash().matches("[0-9a-f]{64}"));
        assertNotEquals(issued.rawToken(), saved.getTokenHash());
        assertEquals("Mozilla/5.0", saved.getUserAgent());
    }

    @Test
    @DisplayName("cada emissão gera um token diferente")
    void everyIssueIsUnique() {
        assertNotEquals(
                service.issue(USER_ID, "ua").rawToken(), service.issue(USER_ID, "ua").rawToken());
    }

    @Test
    @DisplayName("o TTL configurado define a validade e o maxAge do cookie")
    void ttlComesFromConfiguration() {
        assertEquals(Duration.ofDays(14).toSeconds(), service.getTtlSeconds());
        assertEquals(
                Duration.ofDays(30).toSeconds(),
                new RefreshTokenService(repository, 30).getTtlSeconds());
    }

    @Test
    @DisplayName("user-agent muito longo é truncado em 255 caracteres")
    void longUserAgentIsTruncated() {
        service.issue(USER_ID, "x".repeat(400));
        assertEquals(255, captureSaved().getUserAgent().length());
    }

    @Test
    @DisplayName("user-agent nulo é aceito")
    void nullUserAgentIsAccepted() {
        service.issue(USER_ID, null);
        org.junit.jupiter.api.Assertions.assertNull(captureSaved().getUserAgent());
    }

    @Test
    @DisplayName("rotate revoga o token atual e emite um novo para o mesmo usuário")
    void rotateRevokesAndIssuesANewOne() {
        RefreshToken current = activeToken();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate("token-antigo", "Chrome");

        assertEquals(USER_ID, rotation.userId());
        assertNotNull(rotation.rawToken());
        assertNotEquals("token-antigo", rotation.rawToken());
        assertNotNull(current.getRevokedAt(), "o token anterior é invalidado");
        verify(repository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("token ausente ou em branco é rejeitado sem consultar o banco")
    void blankTokenIsRejected() {
        assertThrows(BadCredentialsException.class, () -> service.rotate(null, "ua"));
        assertThrows(BadCredentialsException.class, () -> service.rotate("", "ua"));
        assertThrows(BadCredentialsException.class, () -> service.rotate("   ", "ua"));
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("token desconhecido é rejeitado")
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.rotate("nao-existe", "ua"));
    }

    @Test
    @DisplayName("reuso de token revogado revoga toda a família do usuário (defesa contra roubo)")
    void reusingARevokedTokenRevokesTheWholeFamily() {
        RefreshToken revoked = activeToken();
        revoked.setRevokedAt(Instant.now().minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThrows(BadCredentialsException.class, () -> service.rotate("roubado", "ua"));
        verify(repository).revokeAllActiveForUser(USER_ID);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token expirado também dispara a revogação da família")
    void expiredTokenRevokesTheFamily() {
        RefreshToken expired = activeToken();
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThrows(BadCredentialsException.class, () -> service.rotate("expirado", "ua"));
        verify(repository).revokeAllActiveForUser(USER_ID);
    }

    @Test
    @DisplayName("revoke marca o token como revogado")
    void revokeMarksTheToken() {
        RefreshToken token = activeToken();
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        service.revoke("token");

        assertNotNull(token.getRevokedAt());
        verify(repository).save(token);
    }

    @Test
    @DisplayName("revoke de token já revogado é idempotente")
    void revokeIsIdempotent() {
        Instant revokedAt = Instant.now().minusSeconds(120);
        RefreshToken token = activeToken();
        token.setRevokedAt(revokedAt);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));

        service.revoke("token");

        assertEquals(revokedAt, token.getRevokedAt());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("revoke de token nulo, em branco ou desconhecido não faz nada")
    void revokeOfMissingTokenIsANoOp() {
        service.revoke(null);
        service.revoke("   ");
        verify(repository, never()).findByTokenHash(any());

        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("nao-existe");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("o mesmo token em claro sempre gera o mesmo hash (lookup determinístico)")
    void hashingIsDeterministic() {
        RefreshTokenService.Issued issued = service.issue(USER_ID, "ua");
        String storedHash = captureSaved().getTokenHash();

        RefreshToken stored = activeToken();
        stored.setTokenHash(storedHash);
        when(repository.findByTokenHash(storedHash)).thenReturn(Optional.of(stored));

        assertNotNull(service.rotate(issued.rawToken(), "ua"));
    }

    @Test
    @DisplayName("isActive da entidade cobre expiração e revogação")
    void refreshTokenActiveContract() {
        assertTrue(activeToken().isActive());

        RefreshToken revoked = activeToken();
        revoked.setRevokedAt(Instant.now());
        org.junit.jupiter.api.Assertions.assertFalse(revoked.isActive());

        RefreshToken expired = activeToken();
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        org.junit.jupiter.api.Assertions.assertFalse(expired.isActive());
    }
}
