package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.util.FusoDoEscritorio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A regra dos três baldes, num lugar só.
 *
 * <p>Pagamentos, despesas e receitas respondem às mesmas duas perguntas — "quanto está vencido, a
 * vencer e pago neste período" e "como isso se distribui pelos meses" — com a mesma regra. Copiá-la
 * em três services faria a divergência ser questão de tempo: alguém corrige o tratamento de
 * Cancelado num lugar, e os outros dois passam a contar dinheiro que ninguém espera, cada um no seu
 * relatório.
 *
 * <p>O que varia entre os três é a entidade e o filtro; o que não pode variar é a classificação. É
 * essa fronteira que esta classe desenha.
 *
 * <p><b>O nome da entidade não vem de fora.</b> Cada service passa uma constante própria — os
 * valores possíveis estão no código, nunca numa requisição. Não é concatenação de entrada do
 * usuário em consulta.
 *
 * <p>"Hoje" vem de {@link FusoDoEscritorio}, e não do relógio da JVM: com o servidor em UTC, entre
 * 21h e 24h de Brasília o dia já virou lá e não aqui — e uma parcela que vence amanhã apareceria
 * como vencida.
 */
@Component
public class ConsultasFinanceiras {

    @PersistenceContext private EntityManager em;

    /**
     * Totais do período.
     *
     * @param entidade nome JPQL da entidade (constante do service que chama), com alias {@code x}
     * @param where predicado adicional já com o alias {@code x}, ou {@code null}
     * @param params parâmetros nomeados usados em {@code where}
     */
    public FinanceSummaryDTO resumo(String entidade, String where, Map<String, Object> params) {
        LocalDate hoje = FusoDoEscritorio.hoje();

        String jpql =
                "SELECT "
                        + soma("x.status = :pendente AND x.dueDate < :hoje")
                        + ", "
                        + contagem("x.status = :pendente AND x.dueDate < :hoje")
                        + ", "
                        + soma("x.status = :pendente AND x.dueDate >= :hoje")
                        + ", "
                        + contagem("x.status = :pendente AND x.dueDate >= :hoje")
                        + ", "
                        + soma("x.status = :pago")
                        + ", "
                        + contagem("x.status = :pago")
                        + " FROM "
                        + entidade
                        + " x WHERE 1 = 1"
                        + (where == null || where.isBlank() ? "" : " AND " + where);

        Query query = em.createQuery(jpql);
        query.setParameter("pendente", PaymentStatus.PENDENTE);
        query.setParameter("pago", PaymentStatus.PAGO);
        query.setParameter("hoje", hoje);
        aplicar(query, params);

        Object[] linha = (Object[]) query.getSingleResult();

        FinanceSummaryDTO dto = new FinanceSummaryDTO();
        dto.setOverdueAmount(valor(linha[0]));
        dto.setOverdueCount(quantidade(linha[1]));
        dto.setUpcomingAmount(valor(linha[2]));
        dto.setUpcomingCount(quantidade(linha[3]));
        dto.setPaidAmount(valor(linha[4]));
        dto.setPaidCount(quantidade(linha[5]));
        dto.setTotalAmount(
                dto.getOverdueAmount().add(dto.getUpcomingAmount()).add(dto.getPaidAmount()));
        return dto;
    }

    /**
     * Série mensal entre {@code de} e {@code ate}, inclusive.
     *
     * @param porPagamento {@code true} agrupa por data de pagamento e só conta o que foi pago
     *     (regime de caixa); {@code false} agrupa por vencimento (competência).
     */
    public List<FinanceTimelinePointDTO> serieMensal(
            String entidade,
            String where,
            Map<String, Object> params,
            LocalDate de,
            LocalDate ate,
            boolean porPagamento) {

        LocalDate hoje = FusoDoEscritorio.hoje();
        String campoData = porPagamento ? "x.paidDate" : "x.dueDate";

        StringBuilder jpql = new StringBuilder("SELECT EXTRACT(YEAR FROM ");
        jpql.append(campoData).append("), EXTRACT(MONTH FROM ").append(campoData).append("), ");
        if (porPagamento) {
            // Atrasado e a vencer não existem no regime de caixa: o que foi pago não está nem um
            // nem outro. Zerados aqui em vez de omitidos, para o formato da resposta não mudar
            // conforme o parâmetro — quem consome tipa uma coisa só.
            jpql.append("0, 0, ").append(soma("x.status = :pago"));
        } else {
            jpql.append(soma("x.status = :pendente AND x.dueDate < :hoje"))
                    .append(", ")
                    .append(soma("x.status = :pendente AND x.dueDate >= :hoje"))
                    .append(", ")
                    .append(soma("x.status = :pago"));
        }
        jpql.append(" FROM ").append(entidade).append(" x WHERE ").append(campoData);
        jpql.append(" BETWEEN :de AND :ate");
        if (porPagamento) {
            jpql.append(" AND x.status = :pago");
        }
        if (where != null && !where.isBlank()) {
            jpql.append(" AND ").append(where);
        }
        jpql.append(" GROUP BY EXTRACT(YEAR FROM ")
                .append(campoData)
                .append("), EXTRACT(MONTH FROM ")
                .append(campoData)
                .append(")");

        Query query = em.createQuery(jpql.toString());
        query.setParameter("pago", PaymentStatus.PAGO);
        if (!porPagamento) {
            query.setParameter("pendente", PaymentStatus.PENDENTE);
            query.setParameter("hoje", hoje);
        }
        query.setParameter("de", de);
        query.setParameter("ate", ate);
        aplicar(query, params);

        Map<String, FinanceTimelinePointDTO> porMes = mesesVazios(de, ate);
        for (Object linha : query.getResultList()) {
            Object[] col = (Object[]) linha;
            String chave = YearMonth.of(quantidadeInt(col[0]), quantidadeInt(col[1])).toString();
            FinanceTimelinePointDTO ponto = porMes.get(chave);
            // Fora da janela pedida não deveria acontecer (o BETWEEN já recorta), mas um mês que
            // aparecesse aqui sem lugar no mapa sumiria em silêncio — e some justamente o valor.
            if (ponto == null) continue;
            ponto.setOverdueAmount(valor(col[2]));
            ponto.setUpcomingAmount(valor(col[3]));
            ponto.setPaidAmount(valor(col[4]));
        }
        return new ArrayList<>(porMes.values());
    }

    /**
     * Todos os meses do intervalo, zerados e em ordem.
     *
     * <p>É o que garante que mês sem movimento apareça no gráfico. Sem ele, dois meses distantes
     * ficam colados lado a lado e o mês vazio — que costuma ser a informação — desaparece.
     */
    private static Map<String, FinanceTimelinePointDTO> mesesVazios(LocalDate de, LocalDate ate) {
        Map<String, FinanceTimelinePointDTO> meses = new LinkedHashMap<>();
        YearMonth atual = YearMonth.from(de);
        YearMonth fim = YearMonth.from(ate);
        while (!atual.isAfter(fim)) {
            meses.put(atual.toString(), new FinanceTimelinePointDTO(atual.toString()));
            atual = atual.plusMonths(1);
        }
        return meses;
    }

    private static String soma(String condicao) {
        return "COALESCE(SUM(CASE WHEN " + condicao + " THEN x.amount ELSE 0 END), 0)";
    }

    private static String contagem(String condicao) {
        return "COALESCE(SUM(CASE WHEN " + condicao + " THEN 1L ELSE 0L END), 0L)";
    }

    private static void aplicar(Query query, Map<String, Object> params) {
        if (params == null) return;
        params.forEach(query::setParameter);
    }

    private static BigDecimal valor(Object bruto) {
        if (bruto == null) return BigDecimal.ZERO;
        return bruto instanceof BigDecimal b ? b : new BigDecimal(bruto.toString());
    }

    private static long quantidade(Object bruto) {
        return bruto == null ? 0L : ((Number) bruto).longValue();
    }

    private static int quantidadeInt(Object bruto) {
        return bruto == null ? 0 : ((Number) bruto).intValue();
    }
}
