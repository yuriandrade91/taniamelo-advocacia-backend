package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.AppointmentHistoryDTO;
import com.lawfirm.law.firm.dto.AppointmentRequestDTO;
import com.lawfirm.law.firm.dto.AppointmentResponseDTO;
import com.lawfirm.law.firm.dto.AppointmentSearchParams;
import com.lawfirm.law.firm.dto.AppointmentSummaryDTO;
import com.lawfirm.law.firm.exception.BusinessErrorCode;
import com.lawfirm.law.firm.exception.BusinessException;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Appointment;
import com.lawfirm.law.firm.model.AppointmentAction;
import com.lawfirm.law.firm.model.AppointmentHistory;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.repository.AppointmentHistoryRepository;
import com.lawfirm.law.firm.repository.AppointmentRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.support.TestFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppointmentService: agenda do escritório")
class AppointmentServiceTest {

    private static final UUID APPOINTMENT_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant FUTURE = Instant.parse("2099-08-20T14:30:00Z");
    private static final Instant PAST = Instant.parse("2020-01-01T10:00:00Z");

    @Mock private AppointmentRepository repository;
    @Mock private AppointmentHistoryRepository historyRepository;
    @Mock private ClientRepository clientRepository;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repository, historyRepository, clientRepository);
        when(repository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        when(clientRepository.findById(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(TestFixtures.client()));
        when(clientRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private AppointmentRequestDTO requestDto() {
        AppointmentRequestDTO dto = new AppointmentRequestDTO();
        dto.setTitle("Perícia INSS");
        dto.setType(AppointmentType.PERICIA);
        dto.setStartAt(FUTURE);
        dto.setEndAt(FUTURE.plusSeconds(3600));
        dto.setModality(AppointmentModality.ONLINE);
        dto.setLocation("Agência Centro");
        dto.setMeetingUrl("https://meet.example/abc");
        dto.setDescription("levar documentos");
        return dto;
    }

    private Appointment existing() {
        Appointment appointment = TestFixtures.appointment();
        appointment.setStatus(AppointmentStatus.AGENDADO);
        return appointment;
    }

    private Appointment captureSaved() {
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private AppointmentHistory captureHistory() {
        ArgumentCaptor<AppointmentHistory> saved =
                ArgumentCaptor.forClass(AppointmentHistory.class);
        verify(historyRepository).save(saved.capture());
        return saved.getValue();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("nasce Agendado, com todos os campos aplicados e autor registrado")
        void createsAsScheduled() {
            authenticate();

            AppointmentResponseDTO response = service.create(requestDto());

            assertEquals("Agendado", response.getStatus());
            assertEquals("Perícia INSS", response.getTitle());
            assertEquals("Perícia", response.getType());
            assertEquals("Online", response.getModality());
            assertEquals("Agência Centro", response.getLocation());
            assertEquals("https://meet.example/abc", response.getMeetingUrl());
            assertEquals(TestFixtures.USER_ID, captureSaved().getCreatedBy());
            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("sem modalidade informada, assume Presencial")
        void defaultsModalityToPresencial() {
            AppointmentRequestDTO dto = requestDto();
            dto.setModality(null);

            assertEquals(AppointmentModality.PRESENCIAL, captureAfterCreate(dto).getModality());
        }

        private Appointment captureAfterCreate(AppointmentRequestDTO dto) {
            service.create(dto);
            return captureSaved();
        }

        @Test
        @DisplayName("com clientId válido, o nome livre é descartado (o nome vem do cliente)")
        void linkedClientDiscardsFreeTextName() {
            AppointmentRequestDTO dto = requestDto();
            dto.setClientId(TestFixtures.CLIENT_ID);
            dto.setClientName("Nome digitado à mão");

            Client client = TestFixtures.client();
            when(clientRepository.findAllById(any())).thenReturn(List.of(client));

            AppointmentResponseDTO response = service.create(dto);

            Appointment saved = captureSaved();
            assertEquals(TestFixtures.CLIENT_ID, saved.getClientId());
            assertNull(saved.getClientName());
            assertEquals("Maria da Silva", response.getClientName(), "nome vem do cliente");
        }

        @Test
        @DisplayName("sem clientId, aceita o nome livre (pessoa ainda não cadastrada)")
        void freeTextNameIsKeptWhenThereIsNoClient() {
            AppointmentRequestDTO dto = requestDto();
            dto.setClientName("  Yuri Andrade  ");

            AppointmentResponseDTO response = service.create(dto);

            assertNull(captureSaved().getClientId());
            assertEquals("Yuri Andrade", captureSaved().getClientName(), "com trim");
            assertEquals("Yuri Andrade", response.getClientName());
        }

        @Test
        @DisplayName("nome livre em branco vira null")
        void blankFreeTextNameBecomesNull() {
            AppointmentRequestDTO dto = requestDto();
            dto.setClientName("   ");

            service.create(dto);
            assertNull(captureSaved().getClientName());
        }

        @Test
        @DisplayName("clientId inexistente estoura 404 - nunca grava FK inválida")
        void unknownClientIdThrows() {
            UUID unknown = UUID.randomUUID();
            when(clientRepository.findById(unknown)).thenReturn(Optional.empty());
            AppointmentRequestDTO dto = requestDto();
            dto.setClientId(unknown);

            assertThrows(NotFoundException.class, () -> service.create(dto));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("data no passado sem ciência confirmada vira 422")
        void pastDateWithoutAcknowledgementIsRejected() {
            AppointmentRequestDTO dto = requestDto();
            dto.setStartAt(PAST);
            dto.setEndAt(PAST.plusSeconds(3600));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
            assertEquals(BusinessErrorCode.PAST_DATE_NOT_CONFIRMED, ex.getErrorCode());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("com ciência confirmada, registra quem autorizou e grava no histórico")
        void pastDateWithAcknowledgementIsRecorded() {
            AppointmentRequestDTO dto = requestDto();
            dto.setStartAt(PAST);
            dto.setEndAt(PAST.plusSeconds(3600));
            dto.setPastDateAcknowledged(true);
            authenticate();

            service.create(dto);

            Appointment saved = captureSaved();
            assertEquals(TestFixtures.USER_ID, saved.getPastDateAuthorizedBy());
            assertNotNull(saved.getPastDateAuthorizedAt());

            AppointmentHistory history = captureHistory();
            assertEquals(AppointmentAction.ACKNOWLEDGED, history.getAction());
            assertTrue(history.getJustification().contains("Ciência confirmada"));
            assertEquals(TestFixtures.USER_ID, history.getChangedBy());
        }

        @Test
        @DisplayName("compromisso futuro não registra autorização retroativa")
        void futureAppointmentHasNoRetroactiveAuthorization() {
            service.create(requestDto());

            Appointment saved = captureSaved();
            assertNull(saved.getPastDateAuthorizedBy());
            assertNull(saved.getPastDateAuthorizedAt());
        }
    }

    @Nested
    @DisplayName("list")
    class ListAppointments {

        @SuppressWarnings("unchecked")
        @BeforeEach
        void stubFindAll() {
            when(repository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(existing())));
        }

        private AppointmentSearchParams params() {
            return new AppointmentSearchParams();
        }

        @SuppressWarnings("unchecked")
        private Pageable capturePageable() {
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findAll(any(Specification.class), pageable.capture());
            return pageable.getValue();
        }

        @Test
        @DisplayName("ordena por início crescente e converte a página 1-based")
        void sortsByStartAscending() {
            assertEquals(1, service.list(params()).getContent().size());

            Pageable pageable = capturePageable();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(10, pageable.getPageSize());
            assertEquals(
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.ASC, "startAt"),
                    pageable.getSort());
        }

        @Test
        @DisplayName("tamanho de página inválido cai no default de 10")
        void invalidPageSizeFallsBackToTen() {
            AppointmentSearchParams params = params();
            params.setPageSize(0);
            service.list(params);
            assertEquals(10, capturePageable().getPageSize());
        }

        @Test
        @DisplayName("filtros de ano/mês, tipo, situação, cliente e busca são combinados")
        void combinesEveryFilter() {
            AppointmentSearchParams params = params();
            params.setYear(List.of(2026, 2027));
            params.setMonth(List.of(1, 12));
            params.setType(List.of("Entrevista", "PERICIA"));
            params.setStatus(List.of("Agendado"));
            params.setClientId(TestFixtures.CLIENT_ID);
            params.setSearchTerm("perícia");

            assertNotNull(service.list(params));
        }

        @Test
        @DisplayName("sem ano/mês, o período usa from/to")
        void fallsBackToFromToWhenNoYearOrMonth() {
            AppointmentSearchParams params = params();
            params.setFrom("2026-01-01");
            params.setTo("2026-12-31");

            assertNotNull(service.list(params));
        }

        @Test
        @DisplayName("from inválido vira 400")
        void invalidFromThrows() {
            AppointmentSearchParams params = params();
            params.setFrom("ontem");

            ValidationException ex =
                    assertThrows(ValidationException.class, () -> service.list(params));
            assertEquals("from", ex.getField());
        }

        @Test
        @DisplayName("mês fora de 1-12 vira 400")
        void monthOutOfRangeThrows() {
            for (Integer month : new Integer[] {0, 13, -1, null}) {
                AppointmentSearchParams params = params();
                params.setMonth(java.util.Arrays.asList(month));

                ValidationException ex =
                        assertThrows(ValidationException.class, () -> service.list(params));
                assertEquals("month", ex.getField());
                assertEquals(ValidationErrorCode.INVALID_DATE, ex.getValidationErrorCode());
            }
        }

        @Test
        @DisplayName("tipo inválido no filtro vira 400 explicando os valores aceitos")
        void invalidTypeFilterThrows() {
            AppointmentSearchParams params = params();
            params.setType(List.of("Churrasco"));

            ValidationException ex =
                    assertThrows(ValidationException.class, () -> service.list(params));
            assertEquals("type", ex.getField());
            assertEquals(ValidationErrorCode.INVALID_ENUM_VALUE, ex.getValidationErrorCode());
            assertTrue(ex.getMessage().contains("Entrevista"));
        }

        @Test
        @DisplayName("situação inválida no filtro vira 400")
        void invalidStatusFilterThrows() {
            AppointmentSearchParams params = params();
            params.setStatus(List.of("Talvez"));

            assertEquals(
                    "status",
                    assertThrows(ValidationException.class, () -> service.list(params)).getField());
        }

        @Test
        @DisplayName("valores em branco na lista de filtro são ignorados")
        void blankFilterValuesAreSkipped() {
            AppointmentSearchParams params = params();
            params.setType(java.util.Arrays.asList("Entrevista", null, "   "));

            assertNotNull(service.list(params));
        }

        @Test
        @DisplayName("os nomes dos clientes vinculados vêm em uma única consulta (sem N+1)")
        void resolvesClientNamesInASingleQuery() {
            Appointment first = existing();
            first.setClientId(TestFixtures.CLIENT_ID);
            Appointment second = existing();
            second.setClientId(TestFixtures.CLIENT_ID);

            when(repository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(first, second)));
            when(clientRepository.findAllById(any())).thenReturn(List.of(TestFixtures.client()));

            List<AppointmentResponseDTO> content = service.list(params()).getContent();

            assertEquals("Maria da Silva", content.get(0).getClientName());
            assertEquals("Maria da Silva", content.get(1).getClientName());
            verify(clientRepository, times(1)).findAllById(any());
        }
    }

    @Nested
    @DisplayName("summary por mês")
    class Summary {

        @Test
        @DisplayName("conta só os pendentes (Agendado), agrupados por mês")
        void countsOnlyScheduledGroupedByMonth() {
            Appointment jan = existing();
            jan.setStartAt(Instant.parse("2026-01-15T10:00:00Z"));
            Appointment jan2 = existing();
            jan2.setStartAt(Instant.parse("2026-01-20T10:00:00Z"));
            Appointment mar = existing();
            mar.setStartAt(Instant.parse("2026-03-05T10:00:00Z"));

            when(repository.findByDeletedAtIsNullAndStartAtBetween(any(), any()))
                    .thenReturn(List.of(jan, jan2, mar));

            List<AppointmentSummaryDTO> summary = service.summary(2026);

            assertEquals(2, summary.size());
            assertEquals(new AppointmentSummaryDTO(2026, 1, 2L), summary.get(0));
            assertEquals(new AppointmentSummaryDTO(2026, 3, 1L), summary.get(1));
        }

        @Test
        @DisplayName("concluídos e cancelados não entram na contagem")
        void ignoresCompletedAndCancelled() {
            Appointment done = existing();
            done.setStatus(AppointmentStatus.CONCLUIDO);
            done.setStartAt(Instant.parse("2026-02-01T10:00:00Z"));
            Appointment cancelled = existing();
            cancelled.setStatus(AppointmentStatus.CANCELADO);
            cancelled.setStartAt(Instant.parse("2026-02-02T10:00:00Z"));

            when(repository.findByDeletedAtIsNullAndStartAtBetween(any(), any()))
                    .thenReturn(List.of(done, cancelled));

            assertTrue(service.summary(2026).isEmpty());
        }

        @Test
        @DisplayName("a janela consultada cobre o ano inteiro em UTC")
        void queriesTheWholeYear() {
            when(repository.findByDeletedAtIsNullAndStartAtBetween(any(), any()))
                    .thenReturn(List.of());

            service.summary(2026);

            ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
            verify(repository).findByDeletedAtIsNullAndStartAtBetween(from.capture(), to.capture());
            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), from.getValue());
            assertEquals(Instant.parse("2026-12-31T23:59:59.999999999Z"), to.getValue());
        }

        @Test
        @DisplayName("ano sem compromissos devolve lista vazia")
        void emptyYearReturnsEmptyList() {
            when(repository.findByDeletedAtIsNullAndStartAtBetween(any(), any()))
                    .thenReturn(List.of());
            assertTrue(service.summary(2030).isEmpty());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("exige justificativa e a registra no histórico")
        void requiresJustificationAndRecordsIt() {
            Appointment appointment = existing();
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(appointment));
            authenticate();

            AppointmentRequestDTO dto = requestDto();
            dto.setJustification("  cliente pediu para adiar  ");

            service.update(APPOINTMENT_ID, dto);

            AppointmentHistory history = captureHistory();
            assertEquals(AppointmentAction.EDITED, history.getAction());
            assertEquals("cliente pediu para adiar", history.getJustification(), "com trim");
            assertEquals(TestFixtures.USER_ID, appointment.getUpdatedBy());
        }

        @Test
        @DisplayName("sem justificativa vira 400")
        void missingJustificationThrows() {
            Appointment appointment = existing();
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(appointment));

            for (String justification : new String[] {null, "", "   "}) {
                AppointmentRequestDTO dto = requestDto();
                dto.setJustification(justification);

                ValidationException ex =
                        assertThrows(
                                ValidationException.class,
                                () -> service.update(APPOINTMENT_ID, dto));
                assertEquals("justification", ex.getField());
                assertEquals(ValidationErrorCode.REQUIRED_FIELD, ex.getValidationErrorCode());
            }
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("mover para uma data no passado sem ciência vira 422")
        void movingToThePastWithoutAcknowledgementIsRejected() {
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(existing()));

            AppointmentRequestDTO dto = requestDto();
            dto.setJustification("remarcado");
            dto.setStartAt(PAST);
            dto.setEndAt(PAST.plusSeconds(3600));

            assertEquals(
                    BusinessErrorCode.PAST_DATE_NOT_CONFIRMED,
                    assertThrows(BusinessException.class, () -> service.update(APPOINTMENT_ID, dto))
                            .getErrorCode());
        }

        @Test
        @DisplayName("mover para o passado com ciência gera dois registros no histórico")
        void movingToThePastWithAcknowledgementRecordsBoth() {
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(existing()));

            AppointmentRequestDTO dto = requestDto();
            dto.setJustification("registro retroativo");
            dto.setStartAt(PAST);
            dto.setEndAt(PAST.plusSeconds(3600));
            dto.setPastDateAcknowledged(true);

            service.update(APPOINTMENT_ID, dto);

            ArgumentCaptor<AppointmentHistory> history =
                    ArgumentCaptor.forClass(AppointmentHistory.class);
            verify(historyRepository, times(2)).save(history.capture());
            assertEquals(AppointmentAction.EDITED, history.getAllValues().get(0).getAction());
            assertEquals(AppointmentAction.ACKNOWLEDGED, history.getAllValues().get(1).getAction());
        }

        @Test
        @DisplayName("compromisso inexistente ou excluído estoura 404")
        void missingAppointmentThrows() {
            when(repository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
            AppointmentRequestDTO dto = requestDto();
            dto.setJustification("x");
            UUID unknown = UUID.randomUUID();

            NotFoundException ex =
                    assertThrows(NotFoundException.class, () -> service.update(unknown, dto));
            assertTrue(ex.getMessage().startsWith("Compromisso"));
        }
    }

    @Nested
    @DisplayName("cancel / complete / delete")
    class StateTransitions {

        private Appointment appointment;

        @BeforeEach
        void stub() {
            appointment = existing();
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(appointment));
        }

        @Test
        @DisplayName("cancelar exige justificativa, grava o motivo e registra o histórico")
        void cancelRecordsReasonAndHistory() {
            authenticate();

            AppointmentResponseDTO response =
                    service.cancel(APPOINTMENT_ID, "  cliente desistiu  ");

            assertEquals("Cancelado", response.getStatus());
            assertEquals("cliente desistiu", appointment.getCancellationReason());
            assertEquals(TestFixtures.USER_ID, appointment.getUpdatedBy());

            AppointmentHistory history = captureHistory();
            assertEquals(AppointmentAction.CANCELLED, history.getAction());
            assertEquals("cliente desistiu", history.getJustification());
        }

        @Test
        @DisplayName("cancelar sem justificativa vira 400")
        void cancelWithoutJustificationThrows() {
            assertEquals(
                    "justification",
                    assertThrows(
                                    ValidationException.class,
                                    () -> service.cancel(APPOINTMENT_ID, "  "))
                            .getField());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("cancelar duas vezes vira 422")
        void cancellingTwiceIsRejected() {
            appointment.setStatus(AppointmentStatus.CANCELADO);

            BusinessException ex =
                    assertThrows(
                            BusinessException.class,
                            () -> service.cancel(APPOINTMENT_ID, "de novo"));
            assertEquals(BusinessErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("já está cancelado"));
        }

        @Test
        @DisplayName("concluir marca Concluído e não exige justificativa")
        void completeMarksAsDone() {
            authenticate();

            assertEquals("Concluído", service.complete(APPOINTMENT_ID).getStatus());
            assertEquals(AppointmentStatus.CONCLUIDO, appointment.getStatus());
            assertEquals(TestFixtures.USER_ID, appointment.getUpdatedBy());
            verify(historyRepository, never()).save(any());
        }

        @Test
        @DisplayName("concluir um compromisso cancelado vira 422")
        void completingACancelledAppointmentIsRejected() {
            appointment.setStatus(AppointmentStatus.CANCELADO);

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> service.complete(APPOINTMENT_ID));
            assertEquals(BusinessErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("não pode ser concluído"));
        }

        @Test
        @DisplayName("delete é soft: marca deletedAt e preserva para auditoria")
        void deleteIsSoft() {
            authenticate();

            service.delete(APPOINTMENT_ID);

            assertNotNull(appointment.getDeletedAt());
            assertEquals(TestFixtures.USER_ID, appointment.getUpdatedBy());
            verify(repository).save(appointment);
            verify(repository, never()).delete(any(Appointment.class));
        }

        @Test
        @DisplayName("operações sobre compromisso inexistente estouram 404")
        void missingAppointmentThrows() {
            UUID unknown = UUID.randomUUID();
            when(repository.findByIdAndDeletedAtIsNull(unknown)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.get(unknown));
            assertThrows(NotFoundException.class, () -> service.cancel(unknown, "x"));
            assertThrows(NotFoundException.class, () -> service.complete(unknown));
            assertThrows(NotFoundException.class, () -> service.delete(unknown));
            assertThrows(NotFoundException.class, () -> service.history(unknown, 1, 10));
        }
    }

    @Nested
    @DisplayName("get e histórico")
    class Reads {

        @Test
        @DisplayName("get mapeia o compromisso e resolve o nome do cliente vinculado")
        void getMapsAndResolvesClientName() {
            Appointment appointment = existing();
            appointment.setClientId(TestFixtures.CLIENT_ID);
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(appointment));
            when(clientRepository.findAllById(any())).thenReturn(List.of(TestFixtures.client()));

            AppointmentResponseDTO dto = service.get(APPOINTMENT_ID);

            assertEquals(APPOINTMENT_ID, dto.getId());
            assertEquals("Maria da Silva", dto.getClientName());
            assertEquals("Entrevista", dto.getType());
        }

        @Test
        @DisplayName("compromisso sem cliente vinculado não consulta a tabela de clientes")
        void withoutClientIdNoLookupIsMade() {
            Appointment appointment = existing();
            appointment.setClientName("Alguém");
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(appointment));

            assertEquals("Alguém", service.get(APPOINTMENT_ID).getClientName());
            verify(clientRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("tipo, modalidade e status nulos viram rótulos nulos")
        void nullEnumsMapToNullLabels() {
            Appointment appointment = existing();
            appointment.setType(null);
            appointment.setModality(null);
            appointment.setStatus(null);
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(appointment));

            AppointmentResponseDTO dto = service.get(APPOINTMENT_ID);
            assertNull(dto.getType());
            assertNull(dto.getModality());
            assertNull(dto.getStatus());
        }

        @Test
        @DisplayName("histórico vem do mais recente para o mais antigo")
        void historyIsNewestFirst() {
            AppointmentHistory entry = new AppointmentHistory();
            entry.setAction(AppointmentAction.EDITED);
            entry.setJustification("motivo");
            entry.setChangedAt(Instant.parse("2026-05-01T10:00:00Z"));
            entry.setChangedBy(TestFixtures.USER_ID);

            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(existing()));
            when(historyRepository.findByAppointment_Id(eq(APPOINTMENT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(entry)));

            List<AppointmentHistoryDTO> content =
                    service.history(APPOINTMENT_ID, 1, 10).getContent();

            assertEquals("EDITED", content.get(0).action());
            assertEquals("motivo", content.get(0).justification());
            assertEquals(TestFixtures.USER_ID, content.get(0).changedByUserId());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(historyRepository).findByAppointment_Id(any(), pageable.capture());
            assertEquals(
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC, "changedAt"),
                    pageable.getValue().getSort());
        }

        @Test
        @DisplayName("entrada de histórico sem ação vira action nula no DTO")
        void historyWithoutActionMapsToNull() {
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(existing()));
            when(historyRepository.findByAppointment_Id(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(new AppointmentHistory())));

            assertNull(service.history(APPOINTMENT_ID, 1, 10).getContent().get(0).action());
        }

        @Test
        @DisplayName("paginação inválida no histórico é normalizada")
        void historyNormalizesPaging() {
            when(repository.findByIdAndDeletedAtIsNull(APPOINTMENT_ID))
                    .thenReturn(Optional.of(existing()));
            when(historyRepository.findByAppointment_Id(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.history(APPOINTMENT_ID, 0, 0);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(historyRepository).findByAppointment_Id(any(), pageable.capture());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(10, pageable.getValue().getPageSize());
        }
    }

    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.DisplayName("findConflicts")
    class FindConflicts {

        @Test
        @org.junit.jupiter.api.DisplayName("janela ausente ou invertida não consulta o banco")
        void invalidWindowReturnsEmpty() {
            // É consulta disparada enquanto se digita: devolver vazio é mais útil que 400.
            org.junit.jupiter.api.Assertions.assertTrue(
                    service.findConflicts(null, FUTURE, null).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(
                    service.findConflicts(FUTURE, null, null).isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(
                    service.findConflicts(FUTURE, FUTURE.minusSeconds(3600), null).isEmpty());
            // Mesmo instante não é janela.
            org.junit.jupiter.api.Assertions.assertTrue(
                    service.findConflicts(FUTURE, FUTURE, null).isEmpty());

            org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                    .findAll(
                            org.mockito.ArgumentMatchers
                                    .<org.springframework.data.jpa.domain.Specification<
                                                    Appointment>>
                                            any(),
                            org.mockito.ArgumentMatchers.any(
                                    org.springframework.data.domain.Sort.class));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("janela válida consulta e devolve o que sobrepõe")
        void validWindowQueries() {
            Appointment existing = TestFixtures.appointment();
            when(repository.findAll(
                            org.mockito.ArgumentMatchers
                                    .<org.springframework.data.jpa.domain.Specification<
                                                    Appointment>>
                                            any(),
                            org.mockito.ArgumentMatchers.any(
                                    org.springframework.data.domain.Sort.class)))
                    .thenReturn(List.of(existing));

            var conflicts = service.findConflicts(FUTURE, FUTURE.plusSeconds(3600), null);

            org.junit.jupiter.api.Assertions.assertEquals(1, conflicts.size());
        }
    }
}
