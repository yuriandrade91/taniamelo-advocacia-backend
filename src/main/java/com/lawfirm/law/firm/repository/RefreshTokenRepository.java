package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoga todos os tokens ativos de um usuário (logout global / detecção de reuso). */
    @Modifying
    @Query(
            "update RefreshToken t set t.revokedAt = CURRENT_TIMESTAMP "
                    + "where t.userId = :userId and t.revokedAt is null")
    void revokeAllActiveForUser(@Param("userId") UUID userId);
}
