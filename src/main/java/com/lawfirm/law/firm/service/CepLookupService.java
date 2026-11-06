package com.lawfirm.law.firm.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lawfirm.law.firm.dto.CepLookupResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.SystemErrorCode;
import com.lawfirm.law.firm.exception.SystemException;
import com.lawfirm.law.firm.exception.TooManyAttemptsException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Consulta de CEP, servida pelo nosso backend em vez de o navegador falar com o provedor.
 *
 * <p>Passar por aqui não é cerimônia. Chamando direto do front, a origem de toda consulta vira o
 * computador do escritório — e o provedor público bloqueia por IP quando acha que está sendo
 * raspado, derrubando o preenchimento de endereço para todo mundo ao mesmo tempo. Aqui a consulta
 * sai com cache, com cota e com prazo para responder.
 *
 * <p><b>Cache antes da cota</b>, e não o contrário: quem repete o mesmo CEP não gasta chamada
 * externa nenhuma, então também não faz sentido gastar cota. A cota existe para o caso que custa —
 * CEPs distintos em sequência, que é varredura e não digitação.
 *
 * <p>O cache é compartilhado entre tenants de propósito: CEP é dado público do país, não do
 * escritório. Guardar uma cópia por schema multiplicaria memória e chamadas para responder a mesma
 * coisa.
 *
 * <p>O que NÃO fazemos aqui: gravar endereço. Isto resolve um CEP e devolve os campos; quem salva é
 * {@code POST /clients/{clientId}/addresses}, com as validações dele. Um proxy que escreve seria
 * duas portas para o mesmo dado.
 */
@Service
public class CepLookupService {

    private static final Logger log = LoggerFactory.getLogger(CepLookupService.class);

    /** Oito dígitos. O provedor recusa qualquer outra coisa — recusamos antes de sair da rede. */
    private static final int DIGITOS = 8;

    /*
     * Cota por usuário. Sessenta por minuto é folgado para quem digita um formulário (um CEP leva
     * segundos para ser preenchido) e apertado para um script que percorre faixas de CEP. O limite
     * por hora existe para o ritmo lento: 600/h não se alcança cadastrando clientes, só varrendo.
     */
    private static final int CONSULTAS_POR_MINUTO = 60;
    private static final int CONSULTAS_POR_HORA = 600;

    private final RestClient client;

    /**
     * CEP muda (rua nova, faixa remanejada), mas em semanas, não em horas. Sete dias corta
     * praticamente toda chamada repetida sem servir endereço extinto por muito tempo.
     */
    private final Cache<String, CepLookupResponseDTO> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(7)).maximumSize(20_000).build();

    /** Buckets expiram por inatividade: sem isso o próprio abuso viraria vazamento de memória. */
    private final Cache<String, Bucket> porUsuario =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofHours(2))
                    .maximumSize(10_000)
                    .build();

    public CepLookupService(
            @Value("${app.cep.base-url:https://viacep.com.br/ws}") String baseUrl,
            @Value("${app.cep.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.cep.read-timeout-ms:3000}") int readTimeoutMs) {
        // Prazo curto e explícito: sem ele, um provedor lento segura a thread do Tomcat e o
        // formulário de endereço derruba a aplicação inteira por tabela.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * @param cepBruto como veio da requisição; pontuação é ignorada
     * @param chaveDeQuota quem está consultando (id do usuário) — é por ela que a cota conta
     */
    public CepLookupResponseDTO buscar(String cepBruto, String chaveDeQuota) {
        String cep = normalizar(cepBruto);

        CepLookupResponseDTO cacheado = cache.getIfPresent(cep);
        if (cacheado != null) {
            return cacheado;
        }

        consumirCota(chaveDeQuota);
        CepLookupResponseDTO resolvido = consultarProvedor(cep);
        cache.put(cep, resolvido);
        return resolvido;
    }

    /**
     * Aceita {@code 30240-000}, {@code 30240000} e {@code 30.240-000}; recusa o resto.
     *
     * <p>A validação acontece <b>antes</b> de qualquer rede. Além de responder 400 na hora, é o que
     * garante que nada além de oito dígitos entre na URL do provedor — o caminho é montado por
     * concatenação, e texto arbitrário ali é um caminho para fora do host que queremos.
     */
    private static String normalizar(String cepBruto) {
        String digitos = cepBruto == null ? "" : cepBruto.replaceAll("\\D", "");
        if (digitos.length() != DIGITOS) {
            throw new ValidationException(
                    "cep",
                    ValidationErrorCode.INVALID_ZIP_CODE,
                    "informe 8 dígitos (ex.: 30240-000 ou 30240000); recebido: " + cepBruto);
        }
        return digitos;
    }

    private void consumirCota(String chaveDeQuota) {
        String chave = chaveDeQuota == null || chaveDeQuota.isBlank() ? "anonimo" : chaveDeQuota;
        Bucket bucket =
                porUsuario.get(
                        chave,
                        k ->
                                Bucket.builder()
                                        .addLimit(
                                                Bandwidth.builder()
                                                        .capacity(CONSULTAS_POR_MINUTO)
                                                        .refillGreedy(
                                                                CONSULTAS_POR_MINUTO,
                                                                Duration.ofMinutes(1))
                                                        .build())
                                        .addLimit(
                                                Bandwidth.builder()
                                                        .capacity(CONSULTAS_POR_HORA)
                                                        .refillGreedy(
                                                                CONSULTAS_POR_HORA,
                                                                Duration.ofHours(1))
                                                        .build())
                                        .build());
        if (!bucket.tryConsume(1)) {
            log.warn("Cota de consulta de CEP estourada para {}", chave);
            throw new TooManyAttemptsException(60);
        }
    }

    @SuppressWarnings("unchecked")
    private CepLookupResponseDTO consultarProvedor(String cep) {
        Map<String, Object> corpo;
        try {
            corpo = client.get().uri("/{cep}/json/", cep).retrieve().body(Map.class);
        } catch (RuntimeException ex) {
            // Timeout, DNS, 5xx do provedor. O detalhe vai para o log; para quem chamou é
            // "serviço externo indisponível" — vazar a mensagem do provedor expõe a dependência
            // e não ajuda em nada quem está preenchendo um endereço.
            log.warn("Falha ao consultar CEP {} no provedor: {}", cep, ex.toString());
            throw new SystemException(SystemErrorCode.EXTERNAL_SERVICE_ERROR, ex);
        }

        // O provedor responde 200 com {"erro": true} para CEP inexistente. Tratar isso como
        // sucesso devolveria um endereço de campos nulos, que a tela preencheria em branco sem
        // ninguém perceber que o CEP não existe.
        if (corpo == null || corpo.isEmpty() || corpo.containsKey("erro")) {
            throw new NotFoundException("CEP não encontrado: " + formatar(cep));
        }

        return new CepLookupResponseDTO(
                formatar(cep),
                texto(corpo.get("logradouro")),
                texto(corpo.get("complemento")),
                texto(corpo.get("bairro")),
                texto(corpo.get("localidade")),
                texto(corpo.get("uf")));
    }

    /** No mesmo formato em que o endereço é gravado: {@code 00000-000}. */
    private static String formatar(String digitos) {
        return digitos.substring(0, 5) + "-" + digitos.substring(5);
    }

    /** Campo ausente e campo vazio viram o mesmo nulo: a tela só precisa saber "não veio". */
    private static String texto(Object valor) {
        if (valor == null) return null;
        String s = valor.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
