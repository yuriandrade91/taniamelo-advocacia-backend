package com.lawfirm.law.firm.security;

import com.lawfirm.law.firm.exception.TooManyAttemptsException;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueio temporário da conta após falhas consecutivas.
 *
 * <p>É a metade <b>persistida</b> da defesa contra força bruta: sobrevive a restart e vale para
 * qualquer número de instâncias, porque o estado mora em {@code users}. A outra metade ({@link
 * LoginThrottleService}) reage mais rápido, mas é por instância e some no restart.
 *
 * <p>Bloqueio <b>temporário</b>, e não permanente, por um motivo prático: bloqueio que só um humano
 * destrava vira arma — basta errar a senha da Dra. Tânia cinco vezes para deixá-la fora do sistema
 * até alguém atender o telefone. Quinze minutos inviabilizam o ataque sem inviabilizar o
 * escritório.
 */
@Service
public class AccountLockService {

    private static final Logger log = LoggerFactory.getLogger(AccountLockService.class);

    private static final int FALHAS_ATE_BLOQUEAR = 5;
    private static final Duration DURACAO_DO_BLOQUEIO = Duration.ofMinutes(15);

    private final UserRepository userRepository;

    public AccountLockService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Recusa antes de verificar a senha, se a conta estiver bloqueada.
     *
     * <p>Login inexistente passa direto: aqui não há conta para bloquear, e quem segura esse caso é
     * o limite por IP. Tratar diferente revelaria quais logins existem.
     */
    public void ensureNotLocked(String login) {
        buscar(login)
                .filter(AccountLockService::estaBloqueada)
                .ifPresent(
                        user -> {
                            long espera =
                                    Duration.between(Instant.now(), user.getLockedUntil())
                                            .toSeconds();
                            log.warn("Login recusado: conta {} bloqueada por {}s", login, espera);
                            throw new TooManyAttemptsException(espera);
                        });
    }

    /** Conta a falha e bloqueia ao atingir o limite. */
    @Transactional
    public void registerFailure(String login) {
        buscar(login)
                .ifPresent(
                        user -> {
                            int falhas = valor(user.getFailedLoginAttempts()) + 1;
                            if (falhas >= FALHAS_ATE_BLOQUEAR) {
                                user.setLockedUntil(Instant.now().plus(DURACAO_DO_BLOQUEIO));
                                // Zera junto: ao expirar o bloqueio a conta recomeça com a cota
                                // cheia, em vez de travar de novo na primeira falha seguinte.
                                user.setFailedLoginAttempts(0);
                                log.warn(
                                        "Conta {} bloqueada por {} min após {} falhas",
                                        login,
                                        DURACAO_DO_BLOQUEIO.toMinutes(),
                                        FALHAS_ATE_BLOQUEAR);
                            } else {
                                user.setFailedLoginAttempts(falhas);
                            }
                            userRepository.save(user);
                        });
    }

    /** Acerto limpa o histórico: o limite é de falhas <b>consecutivas</b>. */
    @Transactional
    public void registerSuccess(String login) {
        buscar(login)
                .filter(
                        user ->
                                valor(user.getFailedLoginAttempts()) > 0
                                        || user.getLockedUntil() != null)
                .ifPresent(
                        user -> {
                            user.setFailedLoginAttempts(0);
                            user.setLockedUntil(null);
                            userRepository.save(user);
                        });
    }

    private static boolean estaBloqueada(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private static int valor(Integer falhas) {
        return falhas == null ? 0 : falhas;
    }

    /** O login pode ser e-mail ou username - mesma resolução usada na autenticação. */
    private Optional<User> buscar(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(login, login);
    }
}
