package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.SupportUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Usuários da plataforma (control-plane). Vive em {@code public.support_users}; as consultas não
 * dependem do tenant corrente (a entidade é qualificada com o schema {@code public}).
 */
public interface SupportUserRepository extends JpaRepository<SupportUser, UUID> {

    Optional<SupportUser> findByEmailIgnoreCase(String email);
}
