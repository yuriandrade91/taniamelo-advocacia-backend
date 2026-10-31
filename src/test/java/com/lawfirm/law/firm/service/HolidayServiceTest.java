package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.dto.HolidayCheckDTO;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contra um provedor de mentira com HTTP de verdade, pelo mesmo motivo do teste de CEP. */
@DisplayName("HolidayService: aviso de feriado com cache e tolerância a falha do provedor")
class HolidayServiceTest {

    private static final String HOLIDAYS_2026 =
            """
            [{"date":"2026-12-25","name":"Natal","type":"national","weekday":"sexta-feira"},
             {"date":"2026-11-20","name":"Dia da consciência negra","type":"national"}]
            """;

    private HttpServer provider;
    private HolidayService service;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String responseJson = HOLIDAYS_2026;
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext(
                "/",
                exchange -> {
                    calls.incrementAndGet();
                    byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, body.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        provider.start();
        service =
                new HolidayService(
                        "http://127.0.0.1:" + provider.getAddress().getPort(), 1000, 1000);
    }

    @AfterEach
    void tearDown() {
        provider.stop(0);
    }

    @Test
    @DisplayName("data de feriado devolve o nome e a mensagem para a tela")
    void holidayIsReported() {
        HolidayCheckDTO result = service.check(LocalDate.of(2026, 12, 25));

        assertTrue(result.holiday());
        assertTrue(result.verified());
        assertEquals("Natal", result.name());
        assertTrue(result.message().contains("25/12/2026 é feriado (Natal)"), result.message());
    }

    @Test
    @DisplayName("dia comum avisa que só o calendário nacional foi verificado")
    void ordinaryDaySaysWhatWasNotChecked() {
        HolidayCheckDTO result = service.check(LocalDate.of(2026, 12, 22));

        assertFalse(result.holiday());
        assertTrue(result.verified());
        assertNull(result.name());
        assertTrue(result.message().contains("estaduais e municipais"), result.message());
    }

    @Test
    @DisplayName("dois feriados na mesma data saem juntos, sem derrubar a consulta")
    void twoHolidaysOnTheSameDate() {
        responseJson =
                """
                [{"date":"2019-04-21","name":"Páscoa"},{"date":"2019-04-21","name":"Tiradentes"}]
                """;

        assertEquals("Páscoa / Tiradentes", service.check(LocalDate.of(2019, 4, 21)).name());
    }

    @Test
    @DisplayName("o ano consultado fica em cache: datas do mesmo ano não repetem a chamada")
    void yearIsCached() {
        service.check(LocalDate.of(2026, 12, 25));
        service.check(LocalDate.of(2026, 1, 2));
        assertEquals(1, calls.get());

        service.check(LocalDate.of(2027, 1, 2));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("provedor fora do ar não é erro nem 'não é feriado', e a falha não fica em cache")
    void providerFailureIsNotVerifiedAndNotCached() {
        status = 500;

        HolidayCheckDTO failed = service.check(LocalDate.of(2026, 12, 25));

        assertFalse(failed.verified());
        assertFalse(failed.holiday());
        // É o texto que a tela mostra no toast: tem de mandar tentar de novo.
        assertEquals(
                "A verificação de feriados está indisponível no momento. Tente novamente mais tarde.",
                failed.message());

        status = 200;
        assertTrue(service.check(LocalDate.of(2026, 12, 25)).holiday());
    }

    @Test
    @DisplayName("lista vazia ou resposta inesperada do provedor contam como não verificado")
    void unusableResponsesAreNotVerified() {
        responseJson = "[]";
        assertFalse(service.check(LocalDate.of(2026, 12, 25)).verified());

        responseJson = "[{\"date\":\"25/12/2026\",\"name\":\"Natal\"}]";
        assertFalse(service.check(LocalDate.of(2026, 12, 25)).verified());

        responseJson = "[{\"date\":\"2026-12-25\"}]";
        assertFalse(service.check(LocalDate.of(2026, 12, 25)).verified());
    }

    @Test
    @DisplayName("ano fora da faixa do provedor nem sai para a rede")
    void yearOutOfRangeDoesNotCallTheProvider() {
        HolidayCheckDTO tooOld = service.check(LocalDate.of(1899, 12, 25));

        assertFalse(tooOld.verified());
        assertFalse(service.check(LocalDate.of(2200, 12, 25)).verified());
        assertEquals(0, calls.get());
        // Tentar de novo não resolve ano fora da faixa, então o texto não pode mandar tentar.
        assertEquals(
                "A verificação de feriados cobre apenas os anos de 1900 a 2199.", tooOld.message());
    }
}
