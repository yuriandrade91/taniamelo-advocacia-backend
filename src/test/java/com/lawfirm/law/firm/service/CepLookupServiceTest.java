package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lawfirm.law.firm.dto.CepLookupResponseDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.SystemException;
import com.lawfirm.law.firm.exception.TooManyAttemptsException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A consulta de CEP, contra um provedor de mentira que roda no próprio teste.
 *
 * <p>É servidor HTTP de verdade (o do JDK), e não um mock do {@code RestClient}, porque metade do
 * que importa aqui está no caminho que um mock pularia: o JSON do provedor virando os nomes do
 * nosso cadastro, o {@code {"erro": true}} com status 200, e o 5xx que não pode vazar para quem
 * chamou. Mockar o cliente provaria que o Java chama o método certo e nada sobre o contrato.
 */
@DisplayName("CepLookupService: consulta de CEP com cache, cota e prazo")
class CepLookupServiceTest {

    private HttpServer provedor;
    private String baseUrl;
    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile String respostaJson = "{}";
    private volatile int status = 200;
    private volatile long atrasoMs = 0;

    @BeforeEach
    void subirProvedor() throws IOException {
        provedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provedor.createContext(
                "/",
                troca -> {
                    chamadas.incrementAndGet();
                    if (atrasoMs > 0) {
                        try {
                            Thread.sleep(atrasoMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    byte[] corpo = respostaJson.getBytes(StandardCharsets.UTF_8);
                    troca.getResponseHeaders().add("Content-Type", "application/json");
                    troca.sendResponseHeaders(status, corpo.length);
                    try (OutputStream os = troca.getResponseBody()) {
                        os.write(corpo);
                    }
                });
        provedor.start();
        baseUrl = "http://127.0.0.1:" + provedor.getAddress().getPort();
    }

    @AfterEach
    void derrubarProvedor() {
        provedor.stop(0);
    }

    private CepLookupService service() {
        return new CepLookupService(baseUrl, 2000, 3000);
    }

    @Test
    @DisplayName("traduz os campos do provedor para os nomes do cadastro")
    void mapsProviderFieldsToOurNames() {
        respostaJson =
                """
                {"cep":"30240-000","logradouro":"Rua dos Timbiras","complemento":"lado ímpar",
                 "bairro":"Funcionários","localidade":"Belo Horizonte","uf":"MG"}""";

        CepLookupResponseDTO r = service().buscar("30240-000", "u1");

        assertEquals("30240-000", r.zipCode());
        assertEquals("Rua dos Timbiras", r.street());
        assertEquals("lado ímpar", r.complement());
        assertEquals("Funcionários", r.neighborhood());
        assertEquals("Belo Horizonte", r.city());
        assertEquals("MG", r.state());
    }

    @Test
    @DisplayName("aceita com e sem pontuação, e devolve sempre no formato gravado")
    void acceptsPunctuationAndNormalizes() {
        respostaJson =
                """
                {"logradouro":"Rua X","localidade":"BH","uf":"MG"}""";

        for (String entrada : List.of("30240-000", "30240000", "30.240-000", " 30240000 ")) {
            assertEquals("30240-000", service().buscar(entrada, "u1").zipCode(), entrada);
        }
    }

    @Test
    @DisplayName("CEP fora do formato é 400 e não sai para a rede")
    void invalidCepIsRejectedBeforeNetwork() {
        chamadas.set(0);
        for (String ruim : List.of("123", "30240-0000", "abcdefgh", "", "  ")) {
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> service().buscar(ruim, "u1"));
            assertEquals(ValidationErrorCode.INVALID_ZIP_CODE, ex.getValidationErrorCode());
        }
        assertThrows(ValidationException.class, () -> service().buscar(null, "u1"));
        // O ponto do teste: nenhuma dessas tocou o provedor.
        assertEquals(0, chamadas.get());
    }

    @Test
    @DisplayName("CEP inexistente é 404, não endereço em branco")
    void unknownCepIsNotFound() {
        respostaJson = "{\"erro\": true}";
        assertThrows(NotFoundException.class, () -> service().buscar("99999999", "u1"));
    }

    @Test
    @DisplayName("provedor fora do ar vira erro de serviço externo, sem vazar o motivo")
    void providerFailureIsWrapped() {
        status = 500;
        respostaJson = "{\"motivo\":\"detalhe interno do provedor\"}";

        SystemException ex =
                assertThrows(SystemException.class, () -> service().buscar("30240000", "u1"));
        org.junit.jupiter.api.Assertions.assertFalse(
                String.valueOf(ex.getMessage()).contains("detalhe interno do provedor"),
                "a mensagem do provedor não pode chegar a quem chamou");
    }

    @Test
    @DisplayName("provedor lento estoura o prazo em vez de segurar a thread")
    void slowProviderTimesOut() {
        atrasoMs = 1500;
        CepLookupService curto = new CepLookupService(baseUrl, 500, 500);
        assertThrows(SystemException.class, () -> curto.buscar("30240000", "u1"));
    }

    @Test
    @DisplayName("consulta repetida sai do cache e não chama o provedor de novo")
    void repeatedLookupHitsCache() {
        respostaJson =
                """
                {"logradouro":"Rua X","localidade":"BH","uf":"MG"}""";
        CepLookupService s = service();
        chamadas.set(0);

        for (int i = 0; i < 5; i++) {
            assertEquals("Rua X", s.buscar("30240000", "u1").street());
        }

        assertEquals(1, chamadas.get(), "cinco consultas, uma chamada externa");
    }

    @Test
    @DisplayName("cota estoura com CEPs distintos em sequência, e repetido não gasta cota")
    void quotaCountsOnlyExternalCalls() {
        respostaJson =
                """
                {"logradouro":"Rua X","localidade":"BH","uf":"MG"}""";
        CepLookupService s = service();

        // O mesmo CEP mil vezes: uma chamada externa, uma unidade de cota.
        for (int i = 0; i < 1000; i++) {
            s.buscar("30240000", "u1");
        }

        // CEPs distintos: cada um custa chamada e cota. O limite por minuto é 60.
        assertThrows(
                TooManyAttemptsException.class,
                () -> {
                    for (int i = 0; i < 200; i++) {
                        s.buscar(String.format("3024%04d", i), "u1");
                    }
                });
    }

    @Test
    @DisplayName("a cota é por usuário: um não derruba o outro")
    void quotaIsPerUser() {
        respostaJson =
                """
                {"logradouro":"Rua X","localidade":"BH","uf":"MG"}""";
        CepLookupService s = service();

        assertThrows(
                TooManyAttemptsException.class,
                () -> {
                    for (int i = 0; i < 200; i++) {
                        s.buscar(String.format("4024%04d", i), "abusador");
                    }
                });

        // O segundo usuário continua atendido.
        assertEquals("Rua X", s.buscar("50240000", "inocente").street());
    }

    @Test
    @DisplayName("campo vazio do provedor vira nulo, não string vazia")
    void blankProviderFieldsBecomeNull() {
        respostaJson =
                """
                {"logradouro":"","complemento":"  ","bairro":"Centro","localidade":"BH","uf":"MG"}""";

        CepLookupResponseDTO r = service().buscar("30240000", "u1");

        assertNull(r.street());
        assertNull(r.complement());
        assertEquals("Centro", r.neighborhood());
    }
}
