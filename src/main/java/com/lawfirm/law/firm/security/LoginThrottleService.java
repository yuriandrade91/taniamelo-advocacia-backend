package com.lawfirm.law.firm.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lawfirm.law.firm.exception.TooManyAttemptsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Limite de tentativas de login, por IP e por login informado.
 *
 * <p><b>Só falhas consomem cota.</b> Contar todo acerto também penalizaria quem entra de três
 * aparelhos num dia corrido, e o alvo aqui é quem erra em série — não quem usa o sistema.
 *
 * <p>São duas chaves de propósito. Por <b>IP</b> pega o dicionário rodando contra vários logins da
 * mesma origem; por <b>login</b> pega o ataque distribuído contra uma conta específica, que
 * passaria por baixo do limite de IP. Uma sozinha deixa metade do problema de fora.
 *
 * <p>Estado em memória: a aplicação roda numa instância, e a contagem persistida (bloqueio da
 * conta, em {@code users.locked_until}) é a metade que sobrevive a restart e a mais de uma
 * instância. Esta aqui é a que reage rápido.
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);

    /*
     * Duas cotas diferentes, porque as duas chaves têm naturezas diferentes.
     *
     * O LOGIN é a chave precisa: cinco erros seguidos na mesma conta é ataque, não distração.
     *
     * O IP é a chave grosseira, e num escritório o IP público é UM só - a advogada, a
     * secretária e o estágio saem todos por ele. Apertar o IP no mesmo nível do login faria
     * três pessoas errando a senha na mesma manhã derrubarem o acesso da quarta. O limite de
     * IP existe para o outro caso: o dicionário que troca de login a cada tentativa, e que
     * 30 por minuto já barra bem antes de valer a pena.
     */
    private static final int FALHAS_LOGIN_POR_MINUTO = 5;
    private static final int FALHAS_LOGIN_POR_HORA = 20;

    private static final int FALHAS_IP_POR_MINUTO = 30;
    private static final int FALHAS_IP_POR_HORA = 100;

    /**
     * Buckets expiram por inatividade: sem isso, cada IP e cada login já tentado ficaria em memória
     * para sempre, e o próprio ataque viraria vazamento de memória.
     */
    private final Cache<String, Bucket> porIp =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofHours(2))
                    .maximumSize(50_000)
                    .build();

    private final Cache<String, Bucket> porLogin =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofHours(2))
                    .maximumSize(50_000)
                    .build();

    /**
     * Recusa antes de tentar autenticar, se alguma das cotas já estourou.
     *
     * <p>Consulta sem consumir: quem já está no limite não deve gastar CPU de verificação de senha,
     * que é justamente o que um ataque quer forçar.
     */
    public void ensureAllowed(String ip, String login) {
        long esperaIp = segundosAteLiberar(porIp, chave(ip));
        long esperaLogin = segundosAteLiberar(porLogin, chave(login));
        long espera = Math.max(esperaIp, esperaLogin);
        if (espera > 0) {
            log.warn(
                    "Login recusado por excesso de tentativas (ip={}, login={}), liberando em {}s",
                    ip,
                    login,
                    espera);
            throw new TooManyAttemptsException(espera);
        }
    }

    /** Registra uma falha nas duas chaves. Chamado só quando a autenticação não passou. */
    public void registerFailure(String ip, String login) {
        bucket(porIp, chave(ip), FALHAS_IP_POR_MINUTO, FALHAS_IP_POR_HORA).tryConsume(1);
        bucket(porLogin, chave(login), FALHAS_LOGIN_POR_MINUTO, FALHAS_LOGIN_POR_HORA)
                .tryConsume(1);
    }

    /**
     * Zera a cota do login após um acerto.
     *
     * <p>O IP <b>não</b> é zerado: numa rede compartilhada (o escritório inteiro sai por um IP), um
     * acerto legítimo apagaria o rastro das tentativas erradas que vinham junto.
     */
    public void registerSuccess(String login) {
        porLogin.invalidate(chave(login));
    }

    private long segundosAteLiberar(Cache<String, Bucket> cache, String chave) {
        Bucket bucket = cache.getIfPresent(chave);
        if (bucket == null) {
            return 0;
        }
        EstimationProbe probe = bucket.estimateAbilityToConsume(1);
        return probe.canBeConsumed()
                ? 0
                : Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    }

    private Bucket bucket(Cache<String, Bucket> cache, String chave, int porMinuto, int porHora) {
        return cache.get(chave, k -> novoBucket(porMinuto, porHora));
    }

    private Bucket novoBucket(int porMinuto, int porHora) {
        return Bucket.builder()
                .addLimit(
                        Bandwidth.builder()
                                .capacity(porMinuto)
                                .refillIntervally(porMinuto, Duration.ofMinutes(1))
                                .build())
                .addLimit(
                        Bandwidth.builder()
                                .capacity(porHora)
                                .refillIntervally(porHora, Duration.ofHours(1))
                                .build())
                .build();
    }

    /** Chave normalizada: "Dra.Tania" e "dra.tania" são a mesma conta para quem ataca. */
    private String chave(String valor) {
        return valor == null ? "-" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
