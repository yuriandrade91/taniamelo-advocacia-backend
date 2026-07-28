package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Resolve o usuário pelo identificador de login, que pode ser o e-mail OU o username. */
    Optional<User> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);
}
