package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.OfficeRevenueListItemDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueSearchParams;
import com.lawfirm.law.firm.model.OfficeRevenue;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.ClientPaymentRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.OfficeRevenueRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.util.OfficeClock;
import java.math.BigDecimal;
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
 * As entradas de caixa do escritório.
 *
 * <p>A classe estava com 5% de cobertura — a despesa servia de cobaia das três no {@code
 * FinanceiroIntegrationTest}, e isso cobria o que as três de fato compartilham ({@code
 * FinanceQueries}). A <b>projeção</b> da grade não é compartilhada: a de receita traz o cliente, a
 * de despesa traz fornecedor e categoria. Então ela precisa do próprio teste.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfficeRevenueService: a lista de entradas")
class OfficeRevenueServiceTest {

    @Mock private OfficeRevenueRepository repository;
    @Mock private ClientRepository clientRepository;
    @Mock private ClientPaymentRepository clientPaymentRepository;
    @Mock private FinanceQueries queries;

    private OfficeRevenueService service() {
        return new OfficeRevenueService(
                repository, clientRepository, clientPaymentRepository, queries);
    }

    private OfficeRevenue receita() {
        OfficeRevenue r = new OfficeRevenue();
        TestFixtures.setField(r, "id", UUID.fromString("eeeeeeee-0000-0000-0000-000000000001"));
        r.setDescription("Honorário do acordo");
        r.setAmount(new BigDecimal("2500.00"));
        r.setDueDate(OfficeClock.today().minusDays(3));
        r.setStatus(PaymentStatus.PENDENTE);
        r.setPaymentMethod(PaymentMethod.PIX);
        r.setNotes("observação que NÃO deve chegar na grade");
        r.setClient(TestFixtures.client());
        return r;
    }

    @Test
    @DisplayName("a linha traz o cliente desnormalizado e o overdue derivado")
    void listProjectsTheGridRow() {
        when(repository.findAll(
                        ArgumentMatchers.<Specification<OfficeRevenue>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(receita())));

        List<OfficeRevenueListItemDTO> linhas =
                service().list(new OfficeRevenueSearchParams()).getContent();

        assertEquals(1, linhas.size());
        OfficeRevenueListItemDTO linha = linhas.get(0);
        assertEquals("Maria da Silva", linha.getClientName());
        assertEquals("Honorário do acordo", linha.getDescription());
        assertEquals(0, new BigDecimal("2500.00").compareTo(linha.getAmount()));
        assertEquals("Pendente", linha.getStatus());
        assertTrue(linha.isOverdue(), "vencida há 3 dias e ainda Pendente");
        assertNull(linha.getPaidDate());
    }

    @Test
    @DisplayName("receita sem cliente é caso normal: entrada avulsa do escritório")
    void listToleratesRevenueWithoutClient() {
        OfficeRevenue avulsa = receita();
        avulsa.setClient(null);
        when(repository.findAll(
                        ArgumentMatchers.<Specification<OfficeRevenue>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(avulsa)));

        OfficeRevenueListItemDTO linha =
                service().list(new OfficeRevenueSearchParams()).getContent().get(0);

        assertNull(linha.getClientName());
        assertEquals("Honorário do acordo", linha.getDescription());
    }

    @Test
    @DisplayName("a grade não recebe clientId, forma de origem, observação nem autoria")
    void listDoesNotLeakDetailFields() {
        List<String> campos =
                java.util.Arrays.stream(OfficeRevenueListItemDTO.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName)
                        .toList();

        assertFalse(campos.contains("notes"));
        assertFalse(campos.contains("sourcePaymentId"));
        assertFalse(campos.contains("clientId"));
        assertFalse(campos.contains("paymentMethod"), "receita na grade não mostra forma");
        assertFalse(campos.contains("createdBy"));
        assertFalse(campos.contains("updatedAt"));
    }

    @Test
    @DisplayName("não passa ordenação no Pageable: a ordem vem da especificação")
    void listLeavesOrderToSpecification() {
        when(repository.findAll(
                        ArgumentMatchers.<Specification<OfficeRevenue>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().list(new OfficeRevenueSearchParams());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository)
                .findAll(ArgumentMatchers.<Specification<OfficeRevenue>>any(), captor.capture());
        assertTrue(captor.getValue().getSort().isUnsorted());
        assertEquals(10, captor.getValue().getPageSize());
    }
}
