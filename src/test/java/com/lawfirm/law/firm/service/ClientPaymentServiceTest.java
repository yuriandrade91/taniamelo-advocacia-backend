package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ClientPaymentRequestDTO;
import com.lawfirm.law.firm.dto.ClientPaymentResponseDTO;
import com.lawfirm.law.firm.dto.ClientPaymentUpdateRequestDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.ClientPaymentRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.support.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClientPaymentService: parcelas de honorários e o 'atrasado' derivado")
class ClientPaymentServiceTest {

    private static final UUID PAYMENT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    @Mock private ClientPaymentRepository repository;
    @Mock private ClientRepository clientRepository;

    private ClientPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ClientPaymentService(repository, clientRepository);
        when(clientRepository.findById(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(TestFixtures.client()));
        when(repository.save(any(ClientPayment.class))).thenAnswer(i -> i.getArgument(0));
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

    private ClientPayment existing() {
        ClientPayment payment = new ClientPayment();
        payment.setId(PAYMENT_ID);
        payment.setClient(TestFixtures.client());
        payment.setDescription("Parcela 1/3");
        payment.setAmount(new BigDecimal("500.00"));
        payment.setInstallmentNumber(1);
        payment.setInstallmentTotal(3);
        payment.setDueDate(LocalDate.now().plusDays(10));
        payment.setStatus(PaymentStatus.PENDENTE);
        payment.setPaymentMethod(PaymentMethod.PIX);
        return payment;
    }

    private ClientPayment captureSaved() {
        ArgumentCaptor<ClientPayment> saved = ArgumentCaptor.forClass(ClientPayment.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Nested
    @DisplayName("create")
    class Create {

        private ClientPaymentRequestDTO dto() {
            ClientPaymentRequestDTO dto = new ClientPaymentRequestDTO();
            dto.setDescription("Honorários - entrada");
            dto.setAmount(new BigDecimal("1500.50"));
            dto.setInstallmentNumber(1);
            dto.setInstallmentTotal(3);
            dto.setDueDate(LocalDate.of(2026, 9, 10));
            dto.setPaymentMethod("Pix");
            dto.setNotes("combinado por telefone");
            return dto;
        }

        @Test
        @DisplayName("toda parcela nasce Pendente, com autor registrado")
        void newInstallmentStartsPending() {
            authenticate();

            ClientPaymentResponseDTO response = service.create(TestFixtures.CLIENT_ID, dto());

            assertEquals("Pendente", response.getStatus());
            assertEquals("Pix", response.getPaymentMethod());
            assertEquals(new BigDecimal("1500.50"), response.getAmount());
            assertEquals(1, response.getInstallmentNumber());
            assertEquals(3, response.getInstallmentTotal());
            ClientPayment saved = captureSaved();
            assertEquals(PaymentStatus.PENDENTE, saved.getStatus());
            assertEquals(TestFixtures.USER_ID, saved.getCreatedBy());
            assertNull(saved.getPaidDate());
        }

        @Test
        @DisplayName("forma de pagamento ausente ou em branco fica nula")
        void blankPaymentMethodIsNull() {
            ClientPaymentRequestDTO dto = dto();
            dto.setPaymentMethod("  ");
            assertNull(service.create(TestFixtures.CLIENT_ID, dto).getPaymentMethod());

            ClientPaymentRequestDTO other = dto();
            other.setPaymentMethod(null);
            assertNull(service.create(TestFixtures.CLIENT_ID, other).getPaymentMethod());
        }

        @Test
        @DisplayName("forma de pagamento inválida vira 400")
        void invalidPaymentMethodThrows() {
            ClientPaymentRequestDTO dto = dto();
            dto.setPaymentMethod("Bitcoin");

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.create(TestFixtures.CLIENT_ID, dto));
            assertEquals("paymentMethod", ex.getField());
            assertEquals(ValidationErrorCode.INVALID_ENUM_VALUE, ex.getValidationErrorCode());
            assertTrue(ex.getMessage().contains("Bitcoin"));
        }

        @Test
        @DisplayName("cliente inexistente estoura 404")
        void missingClientThrows() {
            UUID unknown = UUID.randomUUID();
            when(clientRepository.findById(unknown)).thenReturn(Optional.empty());
            ClientPaymentRequestDTO dto = dto();

            assertThrows(NotFoundException.class, () -> service.create(unknown, dto));
        }
    }

    @Nested
    @DisplayName("list / get e o campo overdue")
    class Reading {

        @Test
        @DisplayName("ordena por vencimento crescente e normaliza a paginação")
        void listSortsByDueDate() {
            when(repository.findByClient_IdAndDeletedAtIsNull(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(existing())));

            assertEquals(1, service.list(TestFixtures.CLIENT_ID, 0, 0).getContent().size());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findByClient_IdAndDeletedAtIsNull(any(), pageable.capture());
            assertEquals(
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.ASC, "dueDate"),
                    pageable.getValue().getSort());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(10, pageable.getValue().getPageSize());
        }

        @Test
        @DisplayName("Pendente com vencimento no passado é marcada como atrasada")
        void pendingPastDueIsOverdue() {
            ClientPayment payment = existing();
            payment.setDueDate(LocalDate.now().minusDays(1));
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                            PAYMENT_ID, TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(payment));

            assertTrue(service.get(TestFixtures.CLIENT_ID, PAYMENT_ID).isOverdue());
        }

        @Test
        @DisplayName("Pendente vencendo hoje ainda não está atrasada")
        void pendingDueTodayIsNotOverdue() {
            ClientPayment payment = existing();
            payment.setDueDate(LocalDate.now());
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                            PAYMENT_ID, TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(payment));

            assertFalse(service.get(TestFixtures.CLIENT_ID, PAYMENT_ID).isOverdue());
        }

        @Test
        @DisplayName("parcela paga ou cancelada nunca aparece como atrasada")
        void paidOrCancelledIsNeverOverdue() {
            for (PaymentStatus status : List.of(PaymentStatus.PAGO, PaymentStatus.CANCELADO)) {
                ClientPayment payment = existing();
                payment.setStatus(status);
                payment.setDueDate(LocalDate.now().minusYears(1));
                when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                                PAYMENT_ID, TestFixtures.CLIENT_ID))
                        .thenReturn(Optional.of(payment));

                assertFalse(
                        service.get(TestFixtures.CLIENT_ID, PAYMENT_ID).isOverdue(), status.name());
            }
        }

        @Test
        @DisplayName("parcela sem vencimento não é atrasada")
        void nullDueDateIsNotOverdue() {
            ClientPayment payment = existing();
            payment.setDueDate(null);
            payment.setStatus(PaymentStatus.PENDENTE);
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                            PAYMENT_ID, TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(payment));

            assertFalse(service.get(TestFixtures.CLIENT_ID, PAYMENT_ID).isOverdue());
        }

        @Test
        @DisplayName("status e forma de pagamento nulos viram null no DTO")
        void nullEnumsMapToNullLabels() {
            ClientPayment payment = existing();
            payment.setStatus(null);
            payment.setPaymentMethod(null);
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                            PAYMENT_ID, TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(payment));

            ClientPaymentResponseDTO dto = service.get(TestFixtures.CLIENT_ID, PAYMENT_ID);
            assertNull(dto.getStatus());
            assertNull(dto.getPaymentMethod());
        }

        @Test
        @DisplayName("parcela inexistente ou já excluída estoura 404")
        void missingPaymentThrows() {
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.empty());
            UUID unknown = UUID.randomUUID();

            NotFoundException ex =
                    assertThrows(
                            NotFoundException.class,
                            () -> service.get(TestFixtures.CLIENT_ID, unknown));
            assertTrue(ex.getMessage().startsWith("Parcela"));
        }
    }

    @Nested
    @DisplayName("update (PATCH parcial)")
    class Update {

        private ClientPayment payment;

        @BeforeEach
        void stub() {
            payment = existing();
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(
                            PAYMENT_ID, TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(payment));
        }

        @Test
        @DisplayName("patch vazio preserva todos os campos")
        void emptyPatchKeepsEverything() {
            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, new ClientPaymentUpdateRequestDTO());

            assertEquals("Parcela 1/3", payment.getDescription());
            assertEquals(new BigDecimal("500.00"), payment.getAmount());
            assertEquals(PaymentStatus.PENDENTE, payment.getStatus());
            assertEquals(PaymentMethod.PIX, payment.getPaymentMethod());
        }

        @Test
        @DisplayName("cada campo enviado é aplicado individualmente")
        void appliesEveryProvidedField() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setDescription("Parcela renegociada");
            dto.setAmount(new BigDecimal("750.00"));
            dto.setInstallmentNumber(2);
            dto.setInstallmentTotal(4);
            dto.setDueDate(LocalDate.of(2026, 12, 1));
            dto.setPaymentMethod("Boleto");
            dto.setNotes("renegociado");
            authenticate();

            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto);

            assertEquals("Parcela renegociada", payment.getDescription());
            assertEquals(new BigDecimal("750.00"), payment.getAmount());
            assertEquals(2, payment.getInstallmentNumber());
            assertEquals(4, payment.getInstallmentTotal());
            assertEquals(LocalDate.of(2026, 12, 1), payment.getDueDate());
            assertEquals(PaymentMethod.BOLETO, payment.getPaymentMethod());
            assertEquals("renegociado", payment.getNotes());
            assertEquals(TestFixtures.USER_ID, payment.getUpdatedBy());
        }

        @Test
        @DisplayName("marcar como Pago sem data assume hoje")
        void markingPaidWithoutDateAssumesToday() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setStatus("Pago");

            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto);

            assertEquals(PaymentStatus.PAGO, payment.getStatus());
            assertEquals(LocalDate.now(), payment.getPaidDate());
        }

        @Test
        @DisplayName("marcar como Pago com data usa a data informada")
        void markingPaidWithExplicitDate() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setStatus("Pago");
            dto.setPaidDate(LocalDate.of(2026, 7, 1));

            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto);

            assertEquals(LocalDate.of(2026, 7, 1), payment.getPaidDate());
        }

        @Test
        @DisplayName("voltar para Pendente/Cancelado limpa a data de pagamento")
        void revertingStatusClearsPaidDate() {
            payment.setStatus(PaymentStatus.PAGO);
            payment.setPaidDate(LocalDate.of(2026, 1, 1));

            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setStatus("Pendente");
            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto);

            assertEquals(PaymentStatus.PENDENTE, payment.getStatus());
            assertNull(payment.getPaidDate(), "não dá para estar pago e pendente ao mesmo tempo");
        }

        @Test
        @DisplayName("correção manual: Pendente com data explícita mantém a data enviada")
        void manualCorrectionKeepsExplicitDate() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setStatus("Cancelado");
            dto.setPaidDate(LocalDate.of(2026, 2, 2));

            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto);

            assertEquals(PaymentStatus.CANCELADO, payment.getStatus());
            assertEquals(LocalDate.of(2026, 2, 2), payment.getPaidDate());
        }

        @Test
        @DisplayName("data de pagamento sozinha (sem status) é aplicada")
        void paidDateWithoutStatusIsApplied() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setPaidDate(LocalDate.of(2026, 3, 3));

            service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto);

            assertEquals(LocalDate.of(2026, 3, 3), payment.getPaidDate());
            assertEquals(PaymentStatus.PENDENTE, payment.getStatus());
        }

        @Test
        @DisplayName("status inválido vira 400")
        void invalidStatusThrows() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setStatus("Quitadíssimo");

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto));
            assertEquals("status", ex.getField());
            assertTrue(ex.getMessage().contains("Quitadíssimo"));
        }

        @Test
        @DisplayName("forma de pagamento inválida no update vira 400")
        void invalidPaymentMethodThrows() {
            ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();
            dto.setPaymentMethod("Cheque pré-datado");

            assertEquals(
                    "paymentMethod",
                    assertThrows(
                                    ValidationException.class,
                                    () -> service.update(TestFixtures.CLIENT_ID, PAYMENT_ID, dto))
                            .getField());
        }
    }

    @Test
    @DisplayName("delete é soft: marca deletedAt em vez de remover")
    void deleteIsSoft() {
        ClientPayment payment = existing();
        when(repository.findByIdAndClient_IdAndDeletedAtIsNull(PAYMENT_ID, TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(payment));
        authenticate();

        service.delete(TestFixtures.CLIENT_ID, PAYMENT_ID);

        assertNotNull(payment.getDeletedAt());
        assertEquals(TestFixtures.USER_ID, payment.getUpdatedBy());
        verify(repository).save(payment);
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("cliente inexistente estoura 404 nas operações de leitura e escrita")
    void missingClientThrowsEverywhere() {
        UUID unknown = UUID.randomUUID();
        when(clientRepository.findById(unknown)).thenReturn(Optional.empty());
        ClientPaymentUpdateRequestDTO dto = new ClientPaymentUpdateRequestDTO();

        assertThrows(NotFoundException.class, () -> service.list(unknown, 1, 10));
        assertThrows(NotFoundException.class, () -> service.get(unknown, PAYMENT_ID));
        assertThrows(NotFoundException.class, () -> service.update(unknown, PAYMENT_ID, dto));
        assertThrows(NotFoundException.class, () -> service.delete(unknown, PAYMENT_ID));
    }
}
