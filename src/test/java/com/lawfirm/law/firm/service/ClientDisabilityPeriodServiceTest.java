package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ClientDisabilityPeriodRequestDTO;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientDisabilityPeriod;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.repository.ClientDisabilityPeriodRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.util.OfficeClock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Períodos de deficiência: as regras que o cálculo depende")
class ClientDisabilityPeriodServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();

    private ClientDisabilityPeriodRepository repository;
    private ClientRepository clientRepository;
    private ClientDisabilityPeriodService service;
    private Client client;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ClientDisabilityPeriodRepository.class);
        clientRepository = Mockito.mock(ClientRepository.class);
        service = new ClientDisabilityPeriodService(repository, clientRepository);

        client = new Client();
        client.setId(CLIENT_ID);
        client.setGender(Gender.FEMININO);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(repository.findByClient_IdOrderByStartedOnAsc(CLIENT_ID)).thenReturn(List.of());
        when(repository.save(any(ClientDisabilityPeriod.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ClientDisabilityPeriodRequestDTO request(
            String grade, LocalDate start, LocalDate end) {
        ClientDisabilityPeriodRequestDTO dto = new ClientDisabilityPeriodRequestDTO();
        dto.setGrade(grade);
        dto.setStartedOn(start);
        dto.setEndedOn(end);
        return dto;
    }

    private static ClientDisabilityPeriod stored(
            DisabilityGrade grade, LocalDate start, LocalDate end) {
        ClientDisabilityPeriod p = new ClientDisabilityPeriod();
        p.setId(UUID.randomUUID());
        p.setGrade(grade);
        p.setStartedOn(start);
        p.setEndedOn(end);
        return p;
    }

    @Nested
    @DisplayName("o grau")
    class Grade {

        @Test
        @DisplayName("aceita o rótulo e o nome do enum")
        void acceptsLabelAndEnumName() {
            assertEquals(
                    DisabilityGrade.MODERADA,
                    service.create(
                                    CLIENT_ID,
                                    request("Moderada", LocalDate.of(2000, 1, 1), null))
                            .getGrade());
            assertEquals(
                    DisabilityGrade.GRAVE,
                    service.create(CLIENT_ID, request("GRAVE", LocalDate.of(2000, 1, 1), null))
                            .getGrade());
        }

        @Test
        @DisplayName("recusa \"Sem deficiência\": é destino de conversão, não grau de intervalo")
        void refusesTheNonDisabilityBasis() {
            // Gravá-lo faria um intervalo entrar na soma multiplicando por 1 e
            // inflar o total de tempo do cliente.
            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.create(
                                            CLIENT_ID,
                                            request(
                                                    "Sem deficiência",
                                                    LocalDate.of(2000, 1, 1),
                                                    null)));
            assertEquals("grade", ex.getField());
            assertTrue(ex.getMessage().contains("Leve, Moderada ou Grave"));
        }

        @Test
        @DisplayName("grau desconhecido falha alto, não em silêncio")
        void unknownGradeFailsLoud() {
            assertThrows(
                    ValidationException.class,
                    () ->
                            service.create(
                                    CLIENT_ID, request("Gravíssima", LocalDate.of(2000, 1, 1), null)));
        }
    }

    @Nested
    @DisplayName("as datas")
    class Dates {

        @Test
        @DisplayName("início no futuro é recusado")
        void futureStartIsRejected() {
            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.create(
                                            CLIENT_ID,
                                            request(
                                                    "Leve",
                                                    OfficeClock.today().plusDays(1),
                                                    null)));
            assertEquals("startedOn", ex.getField());
        }

        @Test
        @DisplayName("cessação anterior ao início é recusada")
        void endBeforeStartIsRejected() {
            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.create(
                                            CLIENT_ID,
                                            request(
                                                    "Leve",
                                                    LocalDate.of(2000, 6, 1),
                                                    LocalDate.of(2000, 1, 1))));
            assertEquals("endedOn", ex.getField());
        }

        @Test
        @DisplayName("cessação no futuro é recusada, apontando a opção certa")
        void futureEndIsRejected() {
            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.create(
                                            CLIENT_ID,
                                            request(
                                                    "Leve",
                                                    LocalDate.of(2000, 1, 1),
                                                    OfficeClock.today().plusYears(1))));
            // A mensagem tem de dizer o que fazer: quem tenta datar a cessação no
            // futuro está querendo dizer "ainda está em curso".
            assertTrue(ex.getMessage().contains("em branco"), ex.getMessage());
        }

        @Test
        @DisplayName("sem data de cessação é intervalo em aberto, não erro")
        void absentEndIsOngoing() {
            var created = service.create(CLIENT_ID, request("Grave", LocalDate.of(2000, 1, 1), null));
            assertTrue(created.isOngoing());
            assertEquals(null, created.getEndedOn());
        }
    }

    @Nested
    @DisplayName("a sobreposição")
    class Overlap {

        @Test
        @DisplayName("intervalo dentro de outro é recusado, nomeando o conflitante")
        void insideAnotherIsRejected() {
            when(repository.findByClient_IdOrderByStartedOnAsc(CLIENT_ID))
                    .thenReturn(
                            List.of(
                                    stored(
                                            DisabilityGrade.GRAVE,
                                            LocalDate.of(1990, 1, 1),
                                            LocalDate.of(2009, 12, 31))));

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.create(
                                            CLIENT_ID,
                                            request(
                                                    "Leve",
                                                    LocalDate.of(2000, 1, 1),
                                                    LocalDate.of(2001, 1, 1))));
            // Sem nomear o conflitante, quem cadastrou não sabe qual linha olhar.
            assertTrue(ex.getMessage().contains("1990-01-01"), ex.getMessage());
            assertTrue(ex.getMessage().contains("2009-12-31"), ex.getMessage());
        }

        @Test
        @DisplayName("encostar no dia seguinte é permitido; no mesmo dia, não")
        void adjacentIsFineSameDayIsNot() {
            when(repository.findByClient_IdOrderByStartedOnAsc(CLIENT_ID))
                    .thenReturn(
                            List.of(
                                    stored(
                                            DisabilityGrade.GRAVE,
                                            LocalDate.of(1990, 1, 1),
                                            LocalDate.of(1999, 12, 31))));

            // 2000-01-01 começa no dia seguinte ao fim: sem dia em comum.
            service.create(CLIENT_ID, request("Leve", LocalDate.of(2000, 1, 1), null));

            // 1999-12-31 é o último dia do outro: contaria duas vezes.
            assertThrows(
                    ValidationException.class,
                    () ->
                            service.create(
                                    CLIENT_ID, request("Leve", LocalDate.of(1999, 12, 31), null)));
        }

        @Test
        @DisplayName("um segundo intervalo em aberto sempre colide com o primeiro")
        void twoOngoingAlwaysCollide() {
            // Não precisa de regra própria: dois intervalos sem cessação se
            // estendem ambos até hoje, então têm necessariamente dias em comum.
            // O índice único parcial da V21 é a garantia no banco.
            when(repository.findByClient_IdOrderByStartedOnAsc(CLIENT_ID))
                    .thenReturn(
                            List.of(stored(DisabilityGrade.GRAVE, LocalDate.of(1990, 1, 1), null)));

            assertThrows(
                    ValidationException.class,
                    () ->
                            service.create(
                                    CLIENT_ID, request("Leve", LocalDate.of(2020, 1, 1), null)));
        }
    }

    @Nested
    @DisplayName("a conversão")
    class Conversion {

        @Test
        @DisplayName("a conta vem aberta: cada intervalo com seu fator e seu resultado")
        void showsItsWork() {
            when(repository.findByClient_IdOrderByStartedOnAsc(CLIENT_ID))
                    .thenReturn(
                            List.of(
                                    stored(
                                            DisabilityGrade.GRAVE,
                                            LocalDate.of(1990, 1, 1),
                                            LocalDate.of(2009, 12, 31)),
                                    stored(
                                            DisabilityGrade.LEVE,
                                            LocalDate.of(2010, 1, 1),
                                            LocalDate.of(2019, 12, 31))));

            var dto = service.conversion(CLIENT_ID, null);

            assertEquals(DisabilityGrade.SEM_DEFICIENCIA, dto.getConvertedTo());
            assertEquals(30, dto.getTargetYears());
            assertEquals(2, dto.getLines().size());
            assertEquals("1.50", dto.getLines().get(0).getFactor().toPlainString());
            assertEquals("1.07", dto.getLines().get(1).getFactor().toPlainString());
            // O total é a soma do que está visível — é isso que torna a conta conferível.
            assertEquals(
                    dto.getLines().get(0).getConvertedDays()
                            + dto.getLines().get(1).getConvertedDays(),
                    dto.getTotalDays());
        }

        @Test
        @DisplayName("sem destino, converte para a regra geral")
        void defaultsToGeneralRule() {
            assertEquals(
                    DisabilityGrade.SEM_DEFICIENCIA, service.conversion(CLIENT_ID, null).getConvertedTo());
        }

        @Test
        @DisplayName("sexo sem coluna na lei recusa em vez de devolver número")
        void refusesWithoutLegalBasis() {
            client.setGender(Gender.NAO_BINARIO);
            ValidationException ex =
                    assertThrows(
                            ValidationException.class, () -> service.conversion(CLIENT_ID, null));
            assertEquals("gender", ex.getField());
            assertTrue(ex.getMessage().contains("Não-binário"), ex.getMessage());
        }
    }

    @Nested
    @DisplayName("a frase do total")
    class Label {

        @Test
        @DisplayName("omite o que é zero e usa singular quando é um")
        void omitsZeroAndSingularises() {
            assertEquals(
                    "33 anos, 11 meses e 5 dias",
                    ClientDisabilityPeriodService.label(33, 11, 5));
            assertEquals("1 ano, 1 mês e 1 dia", ClientDisabilityPeriodService.label(1, 1, 1));
            assertEquals("20 anos", ClientDisabilityPeriodService.label(20, 0, 0));
            assertEquals("5 meses e 2 dias", ClientDisabilityPeriodService.label(0, 5, 2));
            assertEquals("0 dias", ClientDisabilityPeriodService.label(0, 0, 0));
        }
    }
}
