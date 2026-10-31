package com.lawfirm.law.firm.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lawfirm.law.firm.dto.HolidayCheckDTO;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Diz se uma data é feriado, para a agenda avisar quem está marcando um compromisso.
 *
 * <p><b>Só feriados nacionais.</b> A BrasilAPI é pública e sem chave, mas não tem estado nem
 * município; quem tem exige cadastro e cobra pelas cidades do interior. Por isso a resposta
 * negativa diz o que não foi verificado: em prazo e perícia, "sem feriado" lido como "sem feriado
 * local" pode custar caro.
 *
 * <p><b>Avisa, não bloqueia</b>, como o conflito de horário. E pelo mesmo motivo a falha do
 * provedor não vira erro: esta é uma checagem de apoio, e ela não pode impedir ninguém de agendar.
 * A resposta sai com {@code verified=false}.
 *
 * <p>A mensagem diz "feriado", sem afirmar a esfera, porque o provedor chama Carnaval de {@code
 * national}, e por lei federal ele não é.
 *
 * <p>O cache é por ano e compartilhado entre tenants: a lista é uma só para o país. Falha não entra
 * no cache, para o provedor voltar a responder assim que se recuperar.
 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    /*
     * A faixa que o provedor aceita. Recusar antes de sair da rede limita as chamadas possíveis a
     * 300 anos, todos cacheáveis: a data vem do usuário, e sem isto cada ano inventado seria uma
     * chamada externa.
     */
    private static final int FIRST_SUPPORTED_YEAR = 1900;
    private static final int LAST_SUPPORTED_YEAR = 2199;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /*
     * Textos para a tela mostrar em toast. São dois porque pedem atitudes diferentes: com o
     * provedor fora, tentar de novo resolve; com o ano fora da faixa, nunca resolve.
     */
    private static final String PROVIDER_UNAVAILABLE =
            "A verificação de feriados está indisponível no momento. Tente novamente mais tarde.";
    private static final String YEAR_UNSUPPORTED =
            "A verificação de feriados cobre apenas os anos de "
                    + FIRST_SUPPORTED_YEAR
                    + " a "
                    + LAST_SUPPORTED_YEAR
                    + ".";

    private final RestClient client;

    /**
     * Calendário só muda com lei nova, o que é raro; trinta dias bastam para enxergar a mudança.
     */
    private final Cache<Integer, Map<LocalDate, String>> holidaysByYear =
            Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofDays(30))
                    .maximumSize(LAST_SUPPORTED_YEAR - FIRST_SUPPORTED_YEAR + 1L)
                    .build();

    public HolidayService(
            @Value("${app.holidays.base-url:https://brasilapi.com.br/api/feriados/v1}")
                    String baseUrl,
            @Value("${app.holidays.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.holidays.read-timeout-ms:3000}") int readTimeoutMs) {
        // Prazo curto e explícito, como no CEP: a tela consulta ao escolher a data, e um provedor
        // lento não pode segurar a thread do Tomcat.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public HolidayCheckDTO check(LocalDate date) {
        if (date.getYear() < FIRST_SUPPORTED_YEAR || date.getYear() > LAST_SUPPORTED_YEAR) {
            return notVerified(date, YEAR_UNSUPPORTED);
        }
        Map<LocalDate, String> holidays = holidaysByYear.getIfPresent(date.getYear());
        if (holidays == null) {
            try {
                holidays = fetch(date.getYear());
            } catch (RuntimeException ex) {
                log.warn("Falha ao consultar feriados de {}: {}", date.getYear(), ex.toString());
                return notVerified(date, PROVIDER_UNAVAILABLE);
            }
            holidaysByYear.put(date.getYear(), holidays);
        }

        String name = holidays.get(date);
        String formatted = DATE.format(date);
        if (name == null) {
            return new HolidayCheckDTO(
                    date,
                    false,
                    null,
                    true,
                    "Nenhum feriado nacional em "
                            + formatted
                            + ". Feriados estaduais e municipais não são verificados.");
        }
        return new HolidayCheckDTO(
                date,
                true,
                name,
                true,
                formatted + " é feriado (" + name + "). Confirme antes de agendar nesta data.");
    }

    private Map<LocalDate, String> fetch(int year) {
        List<Map<String, Object>> body =
                client.get()
                        .uri("/{year}", year)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        // Lista vazia não é "ano sem feriados": todo ano tem. Guardá-la no cache faria a agenda
        // afirmar por um mês que nenhuma data é feriado.
        if (body == null || body.isEmpty()) {
            throw new IllegalStateException("provedor devolveu lista vazia");
        }
        // O merge é necessário: em 2019, Páscoa e Tiradentes caíram no mesmo 21 de abril.
        return body.stream()
                .collect(
                        Collectors.toMap(
                                holiday -> LocalDate.parse(holiday.get("date").toString()),
                                holiday -> holiday.get("name").toString(),
                                (first, second) -> first + " / " + second));
    }

    private static HolidayCheckDTO notVerified(LocalDate date, String message) {
        return new HolidayCheckDTO(date, false, null, false, message);
    }
}
