package com.lawfirm.law.firm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Refresh token opaco, guardado apenas hasheado (SHA-256). Vive no schema do próprio tenant. Não é
 * {@code Auditable}: é infraestrutura de autenticação, não dado de negócio.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
public class RefreshToken {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @Setter
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    @Setter
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    @Setter
    private Instant expiresAt;

    @Column(name = "revoked_at")
    @Setter
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    @Setter
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    @Setter
    private Instant createdAt = Instant.now();

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
