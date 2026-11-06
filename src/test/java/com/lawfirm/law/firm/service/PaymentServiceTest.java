package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.PaymentQueryRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.util.OfficeClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * A visão consolidada de parcelas — a lista do escritório inteiro.
 *
 * <p>Esta classe não tinha teste nenhum (14% de cobertura) justamente enquanto a projeção dela
 * mudava. O que estes casos travam é o contrato da linha: quem aparece, e quem ficou para o
 * detalhe.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService: a lista consolidada de parcelas")
class PaymentServiceTest {

    @Mock private PaymentQueryRepository repository;
    @Mock private FinanceQueries queries;

    private PaymentService service() {
        return new PaymentService(repository, queries);
    }

    private ClientPayment parcela() {
        ClientPayment p = new ClientPayment();
        TestFixtures.setField(p, "id", UUID.fromString("dddddddd-0000-0000-0000-000000000001"));
        p.setClient(TestFixtures.client());
        p.setDescription("Entrada do acordo");
        p.setInstallmentNumber(2);
        p.setInstallmentTotal(6);
        p.setDueDate(OfficeClock.today().minusDays(5));
        p.setAmount(new BigDecimal("500.00"));
        p.setStatus(PaymentStatus.PENDENTE);
        p.setPaymentMethod(PaymentMethod.BOLETO);
        p.setNotes("observação que NÃO deve chegar na grade");
        return p;
    }

    @Test
    @DisplayName("a linha traz cliente, parcela e o overdue derivado")
    void listProjectsTheGridRow() {
        when(repository.findAll(
                        ArgumentMatchers.<Specification<ClientPayment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(parcela())));

        List<PaymentListItemDTO> linhas = service().list(new PaymentSearchParams()).getContent();

        assertEquals(1, linhas.size());
        PaymentListItemDTO linha = linhas.get(0);
        assertEquals(TestFixtures.CLIENT_ID, linha.getClientId());
        assertEquals("Maria da Silva", linha.getClientName());
        assertEquals("Entrada do acordo", linha.getDescription());
        assertEquals(2, linha.getInstallmentNumber());
        assertEquals(6, linha.getInstallmentTotal());
        assertEquals("Boleto", linha.getPaymentMethod());
        assertEquals("Pendente", linha.getStatus());
        // Vencida há 5 dias e ainda Pendente: "Atrasado" não é status, é isto.
        assertTrue(linha.isOverdue());
    }

    @Test
    @DisplayName("a grade não recebe observação nem autoria — isso é do detalhe")
    void listDoesNotLeakDetailFields() {
        when(repository.findAll(
                        ArgumentMatchers.<Specification<ClientPayment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(parcela())));

        List<String> campos =
                java.util.Arrays.stream(PaymentListItemDTO.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName)
                        .toList();

        assertFalse(campos.contains("notes"), "observação é do detalhe");
        assertFalse(campos.contains("createdBy"), "autoria é do detalhe");
        assertEquals(1, service().list(new PaymentSearchParams()).getContent().size());
    }

    @Test
    @DisplayName("não passa ordenação no Pageable: a ordem vem da especificação")
    void listLeavesOrderToSpecification() {
        when(repository.findAll(
                        ArgumentMatchers.<Specification<ClientPayment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().list(new PaymentSearchParams());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository)
                .findAll(ArgumentMatchers.<Specification<ClientPayment>>any(), captor.capture());
        // Um Sort aqui substituiria o CASE de status montado em FinanceSpecifications.
        assertTrue(captor.getValue().getSort().isUnsorted());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("parcela sem cliente não quebra a linha")
    void listToleratesMissingClient() {
        ClientPayment semCliente = parcela();
        semCliente.setClient(null);
        when(repository.findAll(
                        ArgumentMatchers.<Specification<ClientPayment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(semCliente)));

        PaymentListItemDTO linha = service().list(new PaymentSearchParams()).getContent().get(0);

        assertEquals(null, linha.getClientId());
        assertEquals(null, linha.getClientName());
        assertEquals("Entrada do acordo", linha.getDescription());
    }

    @Test
    @DisplayName("data de pagamento nula vira nulo, não hoje")
    void listKeepsNullPaidDate() {
        when(repository.findAll(
                        ArgumentMatchers.<Specification<ClientPayment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(parcela())));

        assertEquals(
                (LocalDate) null,
                service().list(new PaymentSearchParams()).getContent().get(0).getPaidDate());
    }
}
