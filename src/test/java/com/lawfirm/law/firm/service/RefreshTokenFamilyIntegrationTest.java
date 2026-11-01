package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Com banco de verdade porque o defeito é de transação: o repositório mockado confirma a chamada de
 * revogação mesmo quando o rollback a desfaz em seguida.
 */
@DisplayName("Refresh token: o reuso derruba a família inteira")
class RefreshTokenFamilyIntegrationTest extends PostgresIntegrationTest {

    @Autowired private RefreshTokenService service;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("depois do reuso, a outra sessão do usuário também não renova")
    void reusedTokenRevokesOtherSessions() {
        UUID userId = userRepository.findAll().getFirst().getId();
        String stolen = service.issue(userId, "ladrao").rawToken();
        String otherSession = service.issue(userId, "outro-aparelho").rawToken();
        service.rotate(stolen, "ladrao");

        assertThrows(BadCredentialsException.class, () -> service.rotate(stolen, "vitima"));
        assertThrows(
                BadCredentialsException.class,
                () -> service.rotate(otherSession, "outro-aparelho"));
    }
}
