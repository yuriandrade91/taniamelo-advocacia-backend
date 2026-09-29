package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseRequestDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseSearchParams;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.repository.OfficeExpenseRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.util.FusoDoEscritorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Os três baldes, contra um banco de verdade.
 *
 * <p>É teste de integração e não de unidade porque a classificação acontece em SQL: um {@code CASE
 * WHEN} com a data de hoje, agregado pelo banco. Um teste com mocks provaria que o Java chama o
 * método certo e não diria nada sobre a conta — que é a única coisa que importa aqui.
 *
 * <p>A despesa serve de cobaia das três: pagamento, despesa e receita compartilham {@link
 * ConsultasFinanceiras}, então a regra provada aqui é literalmente a mesma que roda nas outras
 * duas. Repetir os mesmos casos três vezes custaria três vezes o tempo de container para afirmar o
 * mesmo código.
 */
@DisplayName("Financeiro: os três baldes são disjuntos e o total fecha")
class FinanceiroIntegrationTest extends PostgresIntegrationTest {

    @Autowired private OfficeExpenseService service;
    @Autowired private OfficeExpenseRepository repository;

    @AfterEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("vencido, a vencer e pago não se sobrepõem; Cancelado fica fora do total")
    void baldesDisjuntos() {
        LocalDate hoje = FusoDoEscritorio.hoje();

        lancar("Vencida", "100.00", hoje.minusDays(3), null, null);
        lancar("Vence hoje", "200.00", hoje, null, null);
        lancar("A vencer", "300.00", hoje.plusDays(7), null, null);
        lancar("Paga", "400.00", hoje.minusDays(10), hoje.minusDays(10), "Pago");
        lancar("Cancelada", "999.00", hoje.minusDays(5), null, "Cancelado");

        FinanceSummaryDTO resumo = service.summary(new OfficeExpenseSearchParams());

        assertEquals(0, new BigDecimal("100.00").compareTo(resumo.getOverdueAmount()));
        assertEquals(1, resumo.getOverdueCount());

        // Vencer HOJE é "a vencer", não "vencido". A fronteira é `< hoje`, e é o caso que mais
        // erra: com `<=`, todo boleto do dia nasce em atraso na tela.
        assertEquals(0, new BigDecimal("500.00").compareTo(resumo.getUpcomingAmount()));
        assertEquals(2, resumo.getUpcomingCount());

        assertEquals(0, new BigDecimal("400.00").compareTo(resumo.getPaidAmount()));
        assertEquals(1, resumo.getPaidCount());

        // O total é redundante de propósito: se um dia não fechar, a regra divergiu.
        assertEquals(0, new BigDecimal("1000.00").compareTo(resumo.getTotalAmount()));
        assertTrue(
                resumo.getTotalAmount()
                                .compareTo(
                                        resumo.getOverdueAmount()
                                                .add(resumo.getUpcomingAmount())
                                                .add(resumo.getPaidAmount()))
                        == 0,
                "total precisa ser a soma exata dos três baldes");
    }

    @Test
    @DisplayName("marcar como paga sem informar a data assume hoje")
    void pagaSemDataAssumeHoje() {
        var criada =
                lancar("Paga agora", "150.00", FusoDoEscritorio.hoje().minusDays(2), null, "Pago");
        assertEquals(FusoDoEscritorio.hoje(), criada.getPaidDate());
    }

    @Test
    @DisplayName("a série mensal traz os meses sem movimento, zerados e em ordem")
    void serieMensalPreencheMesesVazios() {
        LocalDate hoje = FusoDoEscritorio.hoje();
        LocalDate deTresMeses = hoje.minusMonths(3);

        lancar("Antiga", "100.00", deTresMeses, deTresMeses, "Pago");
        lancar("Recente", "250.00", hoje, hoje, "Pago");

        List<FinanceTimelinePointDTO> serie =
                service.timeline(deTresMeses.withDayOfMonth(1), hoje, true);

        // Quatro meses: o do lançamento antigo, dois vazios no meio e o atual. Sem o
        // preenchimento, o gráfico colaria os dois extremos e sumiria justamente com o vazio.
        assertEquals(4, serie.size());
        assertEquals(YearMonth.from(deTresMeses).toString(), serie.get(0).getMonth());
        assertEquals(0, new BigDecimal("100.00").compareTo(serie.get(0).getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(serie.get(1).getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(serie.get(2).getPaidAmount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(serie.get(3).getPaidAmount()));
    }

    @Test
    @DisplayName("no regime de caixa, vencido e a vencer vêm zerados por definição")
    void caixaZeraOsOutrosDoisBaldes() {
        LocalDate hoje = FusoDoEscritorio.hoje();
        lancar("Vencida e não paga", "700.00", hoje.minusDays(30), null, null);
        lancar("Paga", "120.00", hoje.minusDays(2), hoje.minusDays(2), "Pago");

        List<FinanceTimelinePointDTO> serie =
                service.timeline(hoje.minusMonths(1).withDayOfMonth(1), hoje, true);

        for (FinanceTimelinePointDTO ponto : serie) {
            assertEquals(0, BigDecimal.ZERO.compareTo(ponto.getOverdueAmount()));
            assertEquals(0, BigDecimal.ZERO.compareTo(ponto.getUpcomingAmount()));
        }
        BigDecimal pago =
                serie.stream()
                        .map(FinanceTimelinePointDTO::getPaidAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("120.00").compareTo(pago));
    }

    @Test
    @DisplayName("pedir competência e caixa na mesma consulta responde erro, não um palpite")
    void doisRecortesNaMesmaConsulta() {
        OfficeExpenseSearchParams params = new OfficeExpenseSearchParams();
        params.setDueFrom(LocalDate.now());
        params.setPaidFrom(LocalDate.now());

        assertThrows(ValidationException.class, () -> service.summary(params));
        assertThrows(ValidationException.class, () -> service.list(params));
    }

    @Test
    @DisplayName("a composição por categoria cobre só o que foi pago")
    void categoriaSoContaPago() {
        LocalDate hoje = FusoDoEscritorio.hoje();
        lancarCategoria(
                "Aluguel pago",
                "1000.00",
                hoje.minusDays(5),
                hoje.minusDays(5),
                "Pago",
                "Aluguel e condomínio");
        lancarCategoria(
                "Aluguel a pagar", "1000.00", hoje.plusDays(5), null, null, "Aluguel e condomínio");

        OfficeExpenseSearchParams params = new OfficeExpenseSearchParams();
        params.setPaidFrom(hoje.minusMonths(1));
        params.setPaidTo(hoje);

        FinanceSummaryDTO resumo = service.summary(params);

        assertEquals(1, resumo.getByCategory().size());
        assertEquals("Aluguel e condomínio", resumo.getByCategory().get(0).getCategory());
        assertEquals(
                0, new BigDecimal("1000.00").compareTo(resumo.getByCategory().get(0).getAmount()));
    }

    private com.lawfirm.law.firm.dto.OfficeExpenseResponseDTO lancar(
            String descricao,
            String valor,
            LocalDate vencimento,
            LocalDate pagamento,
            String status) {
        return lancarCategoria(descricao, valor, vencimento, pagamento, status, "Outros");
    }

    private com.lawfirm.law.firm.dto.OfficeExpenseResponseDTO lancarCategoria(
            String descricao,
            String valor,
            LocalDate vencimento,
            LocalDate pagamento,
            String status,
            String categoria) {
        OfficeExpenseRequestDTO dto = new OfficeExpenseRequestDTO();
        dto.setDescription(descricao);
        dto.setAmount(new BigDecimal(valor));
        dto.setDueDate(vencimento);
        dto.setPaidDate(pagamento);
        dto.setStatus(status);
        dto.setCategory(categoria);
        return service.create(dto);
    }
}
