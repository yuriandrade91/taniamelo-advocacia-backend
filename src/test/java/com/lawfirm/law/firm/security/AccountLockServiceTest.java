package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.exception.TooManyAttemptsException;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AccountLockService: bloqueio temporário após falhas consecutivas")
class AccountLockServiceTest {

    private static final String LOGIN = "dra.tania";

    @Mock private UserRepository userRepository;

    private AccountLockService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountLockService(userRepository);
        user = new User();
        user.setFullName("Tânia Melo");
        user.setEmail("dra.tania@taniamelo.adv.br");
        user.setUsername(LOGIN);
        user.setPasswordHash("hash");
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(LOGIN, LOGIN))
                .thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("conta liberada passa")
    void unlockedAccountPasses() {
        assertDoesNotThrow(() -> service.ensureNotLocked(LOGIN));
    }

    @Test
    @DisplayName("as quatro primeiras falhas só contam")
    void firstFailuresOnlyCount() {
        for (int i = 1; i <= 4; i++) {
            service.registerFailure(LOGIN);
            assertEquals(i, user.getFailedLoginAttempts());
            assertNull(user.getLockedUntil(), "bloqueou cedo demais, na falha " + i);
        }
        assertDoesNotThrow(() -> service.ensureNotLocked(LOGIN));
    }

    @Test
    @DisplayName("a quinta falha bloqueia por 15 minutos")
    void fifthFailureLocks() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure(LOGIN);
        }

        assertNotNull(user.getLockedUntil());
        long minutos = ChronoUnit.MINUTES.between(Instant.now(), user.getLockedUntil());
        assertTrue(minutos >= 14 && minutos <= 15, "bloqueio de " + minutos + " min");

        // Zera junto: ao expirar, a conta recomeça com a cota cheia em vez de travar
        // de novo na primeira falha seguinte.
        assertEquals(0, user.getFailedLoginAttempts());

        TooManyAttemptsException ex =
                assertThrows(TooManyAttemptsException.class, () -> service.ensureNotLocked(LOGIN));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("bloqueio vencido libera sozinho, sem job nem intervenção")
    void expiredLockReleasesItself() {
        user.setLockedUntil(Instant.now().minusSeconds(60));
        assertDoesNotThrow(() -> service.ensureNotLocked(LOGIN));
    }

    @Test
    @DisplayName("acerto limpa contagem e bloqueio: o limite é de falhas CONSECUTIVAS")
    void successClearsEverything() {
        service.registerFailure(LOGIN);
        service.registerFailure(LOGIN);

        service.registerSuccess(LOGIN);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    @DisplayName("acerto sem falhas anteriores não escreve no banco à toa")
    void successOnCleanAccountDoesNotWrite() {
        service.registerSuccess(LOGIN);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login inexistente passa e não grava nada")
    void unknownLoginIsIgnored() {
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());

        // Não há conta para bloquear, e tratar diferente revelaria quais logins existem.
        // Quem segura este caso é o limite por IP.
        assertDoesNotThrow(() -> service.ensureNotLocked("nao-existe"));
        assertDoesNotThrow(() -> service.registerFailure("nao-existe"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login vazio ou nulo não quebra")
    void blankLoginDoesNotBreak() {
        assertDoesNotThrow(() -> service.ensureNotLocked(null));
        assertDoesNotThrow(() -> service.registerFailure("   "));
    }
}
