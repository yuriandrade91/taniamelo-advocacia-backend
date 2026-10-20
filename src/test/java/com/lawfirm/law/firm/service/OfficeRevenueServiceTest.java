package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueListItemDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueRequestDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueResponseDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueSearchParams;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.OfficeRevenue;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.ClientPaymentRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.OfficeRevenueRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.util.OfficeClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // ── Escrita ──

    private OfficeRevenueRequestDTO pedido() {
        OfficeRevenueRequestDTO dto = new OfficeRevenueRequestDTO();
        dto.setDescription("Parecer avulso");
        dto.setAmount(new BigDecimal("800.00"));
        dto.setDueDate(OfficeClock.today().plusDays(5));
        dto.setNotes("pago na reunião");
        return dto;
    }

    private void salvarDevolveOProprio() {
        when(repository.save(any(OfficeRevenue.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("sem status informado, a receita nasce Pendente e sem data de pagamento")
    void createDefaultsToPending() {
        salvarDevolveOProprio();

        OfficeRevenueResponseDTO criada = service().create(pedido());

        assertEquals("Pendente", criada.getStatus());
        assertNull(criada.getPaidDate());
        assertNull(criada.getPaymentMethod());
        assertNull(criada.getClientId(), "receita avulsa: sem cliente");
        assertNull(criada.getSourcePaymentId());
        assertEquals("pago na reunião", criada.getNotes());
        assertFalse(criada.isOverdue(), "vence daqui a 5 dias");
    }

    @Test
    @DisplayName("marcada como Paga sem data, o pagamento fica com a data de hoje")
    void paidWithoutDateIsPaidToday() {
        salvarDevolveOProprio();
        OfficeRevenueRequestDTO dto = pedido();
        dto.setStatus("Pago");
        dto.setPaymentMethod("PIX");

        OfficeRevenueResponseDTO criada = service().create(dto);

        assertEquals("Pago", criada.getStatus());
        assertEquals(OfficeClock.today(), criada.getPaidDate());
        assertEquals(PaymentMethod.PIX.getLabel(), criada.getPaymentMethod());
    }

    @Test
    @DisplayName("cliente e parcela de origem informados são ligados e voltam no detalhe")
    void linksClientAndSourcePayment() {
        salvarDevolveOProprio();
        UUID parcelaId = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        ClientPayment parcela = TestFixtures.withId(new ClientPayment(), parcelaId);
        when(clientRepository.findById(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(TestFixtures.client()));
        when(clientPaymentRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));

        OfficeRevenueRequestDTO dto = pedido();
        dto.setClientId(TestFixtures.CLIENT_ID);
        dto.setSourcePaymentId(parcelaId);
        OfficeRevenueResponseDTO criada = service().create(dto);

        assertEquals(TestFixtures.CLIENT_ID, criada.getClientId());
        assertEquals("Maria da Silva", criada.getClientName());
        assertEquals(parcelaId, criada.getSourcePaymentId());
    }

    @Test
    @DisplayName("cliente inexistente é 404, não receita gravada sem vínculo")
    void unknownClientIsNotFound() {
        UUID fantasma = UUID.randomUUID();
        when(clientRepository.findById(fantasma)).thenReturn(Optional.empty());
        OfficeRevenueRequestDTO dto = pedido();
        dto.setClientId(fantasma);
        OfficeRevenueService service = service();

        assertThrows(NotFoundException.class, () -> service.create(dto));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("parcela de origem inexistente é 404")
    void unknownSourcePaymentIsNotFound() {
        UUID fantasma = UUID.randomUUID();
        when(clientPaymentRepository.findById(fantasma)).thenReturn(Optional.empty());
        OfficeRevenueRequestDTO dto = pedido();
        dto.setSourcePaymentId(fantasma);
        OfficeRevenueService service = service();

        assertThrows(NotFoundException.class, () -> service.create(dto));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("get devolve o detalhe; id inexistente é 404")
    void getReturnsDetailOrNotFound() {
        OfficeRevenue existente = receita();
        when(repository.findById(existente.getId())).thenReturn(Optional.of(existente));
        UUID fantasma = UUID.randomUUID();
        when(repository.findById(fantasma)).thenReturn(Optional.empty());
        OfficeRevenueService service = service();

        OfficeRevenueResponseDTO detalhe = service.get(existente.getId());
        assertEquals(existente.getId(), detalhe.getId());
        assertEquals("Honorário do acordo", detalhe.getDescription());
        assertEquals(PaymentMethod.PIX.getLabel(), detalhe.getPaymentMethod());
        assertTrue(detalhe.isOverdue());

        assertThrows(NotFoundException.class, () -> service.get(fantasma));
    }

    @Test
    @DisplayName("update reaplica os campos sobre o registro existente")
    void updateReappliesFields() {
        OfficeRevenue existente = receita();
        when(repository.findById(existente.getId())).thenReturn(Optional.of(existente));
        salvarDevolveOProprio();

        OfficeRevenueResponseDTO atualizada = service().update(existente.getId(), pedido());

        assertEquals(existente.getId(), atualizada.getId());
        assertEquals("Parecer avulso", atualizada.getDescription());
        assertEquals(0, new BigDecimal("800.00").compareTo(atualizada.getAmount()));
        assertNull(atualizada.getClientId(), "o pedido não trouxe cliente: o vínculo sai");
    }

    @Test
    @DisplayName("delete é lógico: marca deletedAt e grava, não apaga a linha")
    void deleteIsSoft() {
        OfficeRevenue existente = receita();
        when(repository.findById(existente.getId())).thenReturn(Optional.of(existente));

        service().delete(existente.getId());

        assertNotNull(existente.getDeletedAt());
        verify(repository).save(existente);
        verify(repository, never()).delete(any(OfficeRevenue.class));
    }

    // ── Agregados ──

    @Test
    @DisplayName("summary filtra pelo cliente quando ele vem nos parâmetros")
    void summaryFiltersByClient() {
        OfficeRevenueSearchParams params = new OfficeRevenueSearchParams();
        params.setClientId(TestFixtures.CLIENT_ID);
        params.setDueFrom(LocalDate.of(2026, 1, 1));
        FinanceSummaryDTO esperado = new FinanceSummaryDTO();
        when(queries.summary(eq("OfficeRevenue"), anyString(), anyMap())).thenReturn(esperado);

        assertSame(esperado, service().summary(params));

        ArgumentCaptor<String> where = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> valores = ArgumentCaptor.forClass(Map.class);
        verify(queries).summary(eq("OfficeRevenue"), where.capture(), valores.capture());
        assertTrue(where.getValue().contains("x.client.id = :clienteId"));
        assertTrue(where.getValue().contains("x.dueDate >= :de"));
        assertEquals(TestFixtures.CLIENT_ID, valores.getValue().get("clienteId"));
    }

    @Test
    @DisplayName("summary sem cliente não acrescenta o filtro")
    void summaryWithoutClient() {
        when(queries.summary(eq("OfficeRevenue"), anyString(), anyMap()))
                .thenReturn(new FinanceSummaryDTO());

        service().summary(new OfficeRevenueSearchParams());

        ArgumentCaptor<String> where = ArgumentCaptor.forClass(String.class);
        verify(queries).summary(eq("OfficeRevenue"), where.capture(), anyMap());
        assertFalse(where.getValue().contains("clienteId"));
    }

    @Test
    @DisplayName("timeline delega a série mensal sem filtro adicional")
    void timelineDelegates() {
        LocalDate de = LocalDate.of(2026, 1, 1);
        LocalDate ate = LocalDate.of(2026, 6, 30);
        List<FinanceTimelinePointDTO> serie = List.of(new FinanceTimelinePointDTO("2026-01"));
        when(queries.monthlySeries("OfficeRevenue", null, Map.of(), de, ate, true))
                .thenReturn(serie);

        assertSame(serie, service().timeline(de, ate, true));
    }
}
