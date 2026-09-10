package com.lawfirm.law.firm.exception;

/**
 * Tentativas demais de login. Vira 429 com {@code Retry-After}.
 *
 * <p>É o <b>mesmo</b> erro para as duas defesas — limite por IP/login e conta bloqueada. Distinguir
 * as duas na resposta diria a quem está tentando que aquele login existe: bastaria errar a senha
 * seis vezes e ver a mensagem mudar. Uma resposta só, e o motivo real fica no log.
 */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Muitas tentativas. Tente novamente em alguns minutos.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
