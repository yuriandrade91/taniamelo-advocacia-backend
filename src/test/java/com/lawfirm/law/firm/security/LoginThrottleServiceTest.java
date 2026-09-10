package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginThrottleService: limite de tentativas por IP e por login")
class LoginThrottleServiceTest {

    private LoginThrottleService throttle;

    @BeforeEach
    void setUp() {
        throttle = new LoginThrottleService();
    }

    private void falhar(String ip, String login, int vezes) {
        for (int i = 0; i < vezes; i++) {
            throttle.registerFailure(ip, login);
        }
    }

    @Test
    @DisplayName("primeira tentativa passa, sem estado prévio")
    void firstAttemptIsAllowed() {
        assertDoesNotThrow(() -> throttle.ensureAllowed("10.0.0.1", "dra.tania"));
    }

    @Test
    @DisplayName("cinco falhas seguidas fecham o login")
    void fiveFailuresBlock() {
        falhar("10.0.0.1", "dra.tania", 5);

        TooManyAttemptsException ex =
                assertThrows(
                        TooManyAttemptsException.class,
                        () -> throttle.ensureAllowed("10.0.0.1", "dra.tania"));
        assertTrue(ex.getRetryAfterSeconds() > 0, "sem Retry-After a tela não sabe quanto esperar");
    }

    @Test
    @DisplayName("quatro falhas ainda deixam tentar: o limite é o quinto erro")
    void fourFailuresStillAllow() {
        falhar("10.0.0.1", "dra.tania", 4);
        assertDoesNotThrow(() -> throttle.ensureAllowed("10.0.0.1", "dra.tania"));
    }

    @Test
    @DisplayName("acerto limpa a cota do login")
    void successResetsTheLogin() {
        falhar("10.0.0.1", "dra.tania", 5);
        assertThrows(
                TooManyAttemptsException.class,
                () -> throttle.ensureAllowed("10.0.0.1", "dra.tania"));

        throttle.registerSuccess("dra.tania");

        // De outro IP, para isolar do bucket de IP que continua contando.
        assertDoesNotThrow(() -> throttle.ensureAllowed("10.0.0.9", "dra.tania"));
    }

    @Test
    @DisplayName("o mesmo IP tentando vários logins também é barrado, com cota mais larga")
    void sameIpAcrossManyLoginsIsBlocked() {
        // O ataque de dicionário troca de login a cada tentativa; sem a chave por IP,
        // cada login teria cota nova e o limite não pegaria nada.
        for (int i = 0; i < 30; i++) {
            throttle.registerFailure("10.0.0.7", "usuario" + i);
        }
        assertThrows(
                TooManyAttemptsException.class,
                () -> throttle.ensureAllowed("10.0.0.7", "outro-usuario"));
    }

    @Test
    @DisplayName("o escritório inteiro errando a senha algumas vezes NÃO derruba o IP")
    void aFewOfficeMistakesDoNotBlockTheSharedIp() {
        // Num escritório o IP público é um só. Se o limite de IP fosse tão apertado quanto o
        // de login, três pessoas distraídas na mesma manhã derrubariam o acesso da quarta.
        for (int i = 0; i < 4; i++) {
            throttle.registerFailure("200.1.1.1", "pessoa" + i);
        }
        assertDoesNotThrow(() -> throttle.ensureAllowed("200.1.1.1", "pessoa-que-acertou"));
    }

    @Test
    @DisplayName("o mesmo login atacado de vários IPs também é barrado")
    void sameLoginAcrossManyIpsIsBlocked() {
        // E o ataque distribuído troca de IP a cada tentativa; sem a chave por login,
        // passaria por baixo do limite de IP.
        for (int i = 0; i < 5; i++) {
            throttle.registerFailure("10.0.0." + i, "dra.tania");
        }
        assertThrows(
                TooManyAttemptsException.class,
                () -> throttle.ensureAllowed("192.168.1.1", "dra.tania"));
    }

    @Test
    @DisplayName("outro login, de outro IP, não é afetado")
    void unrelatedLoginIsUnaffected() {
        falhar("10.0.0.1", "dra.tania", 5);
        assertDoesNotThrow(() -> throttle.ensureAllowed("10.0.0.2", "ana.souza"));
    }

    @Test
    @DisplayName("caixa e espaços não criam cota nova")
    void keyIsNormalized() {
        // "Dra.Tania" e "dra.tania " são a mesma conta para quem ataca; se fossem chaves
        // diferentes, bastaria variar a caixa para multiplicar as tentativas.
        falhar("10.0.0.1", "dra.tania", 5);
        assertThrows(
                TooManyAttemptsException.class,
                () -> throttle.ensureAllowed("10.0.0.3", "  DRA.TANIA "));
    }

    @Test
    @DisplayName("login nulo não quebra")
    void nullLoginDoesNotBreak() {
        assertDoesNotThrow(() -> throttle.registerFailure(null, null));
        assertDoesNotThrow(() -> throttle.ensureAllowed(null, null));
    }
}
