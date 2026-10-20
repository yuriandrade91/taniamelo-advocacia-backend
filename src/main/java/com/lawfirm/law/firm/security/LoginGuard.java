package com.lawfirm.law.firm.security;

import org.springframework.stereotype.Component;

/**
 * As duas defesas contra força bruta no login, sempre consultadas e alimentadas juntas: o freio em
 * memória por IP e por login ({@link LoginThrottleService}) e o bloqueio persistido da conta
 * ({@link AccountLockService}). Juntas aqui para que nenhum ponto do fluxo de login lembre de uma e
 * esqueça a outra.
 */
@Component
public class LoginGuard {

    private final LoginThrottleService throttle;
    private final AccountLockService accountLock;

    public LoginGuard(LoginThrottleService throttle, AccountLockService accountLock) {
        this.throttle = throttle;
        this.accountLock = accountLock;
    }

    /** Antes de verificar a senha: quem já estourou o limite não deve gastar CPU de hashing. */
    public void ensureAllowed(String ip, String login) {
        throttle.ensureAllowed(ip, login);
        accountLock.ensureNotLocked(login);
    }

    public void registerFailure(String ip, String login) {
        throttle.registerFailure(ip, login);
        accountLock.registerFailure(login);
    }

    public void registerSuccess(String login) {
        throttle.registerSuccess(login);
        accountLock.registerSuccess(login);
    }
}
