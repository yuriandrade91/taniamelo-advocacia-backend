package com.lawfirm.law.firm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;
    private final long supportPlatformMillis;
    private final long supportSessionMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes,
            @Value("${app.jwt.support-platform-minutes:60}") long supportPlatformMinutes,
            @Value("${app.jwt.support-session-minutes:20}") long supportSessionMinutes) {
        // HS256 requires a key of at least 256 bits (32 chars). Pad defensively so a short
        // dev secret doesn't blow up at startup; production must set a proper 32+ char secret.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMinutes * 60_000;
        this.supportPlatformMillis = supportPlatformMinutes * 60_000;
        this.supportSessionMillis = supportSessionMinutes * 60_000;
    }

    public String generateToken(UUID userId, String email, String role, String tenant) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("role", role)
                .claim("tenant", tenant)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Token de PLATAFORMA do suporte: identifica o agente no control-plane e serve só para abrir
     * sessões de suporte ({@code scope=platform}). Sem claim {@code tenant} - sozinho não acessa
     * dado de nenhum escritório (o {@link JwtAuthenticationFilter} só o aceita em /support/**).
     */
    public String generatePlatformToken(UUID supportUserId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("uid", supportUserId)
                .claim("scope", "platform")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(supportPlatformMillis)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Token de SESSÃO de suporte: impersona um tenant específico com acesso total (role ADMIN),
     * curto por natureza. Carrega o ator real ({@code act}) para a auditoria atribuir a ação a quem
     * de fato a executou, mesmo o agente não existindo na tabela de usuários daquele escritório.
     */
    public String generateSupportSessionToken(UUID supportUserId, String email, String tenant) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("uid", supportUserId)
                .claim("role", "ADMIN")
                .claim("tenant", tenant)
                .claim("scope", "support-session")
                .claim("act", supportUserId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(supportSessionMillis)))
                .signWith(signingKey)
                .compact();
    }

    /** {@code platform}, {@code support-session}, ou {@code null} para token normal de tenant. */
    public String extractScope(String token) {
        return parseClaims(token).get("scope", String.class);
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /** Id no claim {@code uid} (usuário do tenant ou usuário de suporte). */
    public UUID extractUid(String token) {
        String uid = parseClaims(token).get("uid", String.class);
        return uid == null ? null : UUID.fromString(uid);
    }

    /** Id do agente de suporte (claim {@code act}) numa sessão de suporte, ou {@code null}. */
    public UUID extractActingSupportId(String token) {
        String act = parseClaims(token).get("act", String.class);
        return act == null ? null : UUID.fromString(act);
    }

    public long getSupportPlatformMillis() {
        return supportPlatformMillis;
    }

    public long getSupportSessionMillis() {
        return supportSessionMillis;
    }

    /** Schema do tenant embutido no token (claim {@code tenant}), ou {@code null} se ausente. */
    public String extractTenant(String token) {
        return parseClaims(token).get("tenant", String.class);
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration() == null || claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
