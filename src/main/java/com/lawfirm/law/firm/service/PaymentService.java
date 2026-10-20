package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.repository.PaymentQueryRepository;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A visão do escritório sobre o que os clientes têm a receber do INSS.
 *
 * <p>Existe porque pagamento só era alcançável por cliente. "Quanto está atrasado no escritório"
 * não tinha como ser respondido sem abrir cliente por cliente — com 125 clientes, 126 requisições
 * por carregamento, e o custo crescendo com a base. Não é otimização prematura evitar isso; é
 * evitar um N+1 sobre HTTP.
 *
 * <p><b>Somente leitura.</b> Criar e editar continuam em {@code /clients/{clientId}/payments}: uma
 * parcela pertence a um cliente, e a rota agregada é uma consulta. A tela leva para a ficha quando
 * for preciso mexer.
 */
@Service
public class PaymentService {

    private static final String ENTIDADE = "ClientPayment";

    /**
     * {@code ClientPayment} não tem {@code @SQLRestriction} — o filtro de excluídos é explícito em
     * cada consulta. Repetir a condição aqui não é redundância: sem ela, a visão consolidada
     * mostraria parcela apagada, e só ela.
     */
    private static final String SOMENTE_ATIVOS =
            "x.deletedAt IS NULL AND x.client.deletedAt IS NULL";

    private final PaymentQueryRepository repository;
    private final FinanceQueries queries;

    public PaymentService(PaymentQueryRepository repository, FinanceQueries queries) {
        this.repository = repository;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public Page<PaymentListItemDTO> list(PaymentSearchParams params) {
        var pageable = PageRequests.of(params.getPageNumber(), params.getPageSize());
        return repository
                .findAll(PaymentSpecs.of(params), pageable)
                .map(PaymentProjections::toListItem);
    }

    @Transactional(readOnly = true)
    public FinanceSummaryDTO summary(PaymentSearchParams params) {
        Map<String, Object> valores = new HashMap<>();
        StringBuilder where = FinanceFilters.period(SOMENTE_ATIVOS, params, valores);
        if (params.getClientId() != null) {
            where.append(" AND x.client.id = :clienteId");
            valores.put("clienteId", params.getClientId());
        }
        return queries.summary(ENTIDADE, where.toString(), valores);
    }

    @Transactional(readOnly = true)
    public List<FinanceTimelinePointDTO> timeline(
            LocalDate de, LocalDate ate, boolean porPagamento) {
        return queries.monthlySeries(ENTIDADE, SOMENTE_ATIVOS, Map.of(), de, ate, porPagamento);
    }
}
