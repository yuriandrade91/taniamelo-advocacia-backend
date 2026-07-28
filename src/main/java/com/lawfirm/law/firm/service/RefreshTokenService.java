package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.model.RefreshToken;
import com.lawfirm.law.firm.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissão, rotação e revogação de refresh tokens. O token entregue ao cliente é opaco e aleatório;
 * no banco fica apenas o hash SHA-256 (nunca o valor em claro). Rotação: cada uso revoga o anterior
 * e emite um novo. Reuso de um token já revogado é tratado como sinal de roubo → revoga toda a
 * família de tokens do usuário.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${app.jwt.refresh-expiration-days:14}") long refreshExpirationDays) {
        this.repository = repository;
        this.ttl = Duration.ofDays(refreshExpirationDays);
    }

    /** Resultado de uma emissão/rotação: token em claro (só aqui) + validade. */
    public record Issued(String rawToken, Instant expiresAt) {}

    /** Resultado de uma rotação: além do novo token, o usuário dono (para emitir o access). */
    public record Rotation(UUID userId, String rawToken, Instant expiresAt) {}

    @Transactional
    public Issued issue(UUID userId, String userAgent) {
        String raw = randomToken();
        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(raw));
        entity.setExpiresAt(Instant.now().plus(ttl));
        entity.setUserAgent(truncate(userAgent));
        repository.save(entity);
        return new Issued(raw, entity.getExpiresAt());
    }

    @Transactional
    public Rotation rotate(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Refresh token ausente");
        }
        RefreshToken current =
                repository
                        .findByTokenHash(hash(rawToken))
                        .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

        if (!current.isActive()) {
            // Reuso de token revogado/expirado: revoga tudo do usuário (defesa contra roubo).
            repository.revokeAllActiveForUser(current.getUserId());
            throw new BadCredentialsException("Refresh token expirado ou já utilizado");
        }

        current.setRevokedAt(Instant.now());
        repository.save(current);

        Issued next = issue(current.getUserId(), userAgent);
        return new Rotation(current.getUserId(), next.rawToken(), next.expiresAt());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository
                .findByTokenHash(hash(rawToken))
                .ifPresent(
                        token -> {
                            if (token.getRevokedAt() == null) {
                                token.setRevokedAt(Instant.now());
                                repository.save(token);
                            }
                        });
    }

    public long getTtlSeconds() {
        return ttl.toSeconds();
    }

    // ── Helpers ──

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) return null;
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
