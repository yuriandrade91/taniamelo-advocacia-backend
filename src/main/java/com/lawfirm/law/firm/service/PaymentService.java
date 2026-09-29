package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.PaymentListItemDTO;
import com.lawfirm.law.firm.dto.PaymentSearchParams;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.FinanceSpecifications;
import com.lawfirm.law.firm.repository.PaymentQueryRepository;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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
    private final ConsultasFinanceiras consultas;

    public PaymentService(PaymentQueryRepository repository, ConsultasFinanceiras consultas) {
        this.repository = repository;
        this.consultas = consultas;
    }

    public Page<PaymentListItemDTO> list(PaymentSearchParams params) {
        RecorteDeData.exigirUmRecorte(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Specification<ClientPayment> spec =
                Specification.allOf(
                        ativos(),
                        FinanceSpecifications.<ClientPayment>dueBetween(
                                params.getDueFrom(), params.getDueTo()),
                        FinanceSpecifications.<ClientPayment>paidBetween(
                                params.getPaidFrom(), params.getPaidTo()),
                        FinanceSpecifications.<ClientPayment>statusIn(
                                EnumsDeRequisicao.lista(
                                        "status", params.getStatus(), PaymentStatus::fromLabel)),
                        FinanceSpecifications.<ClientPayment>methodIn(
                                EnumsDeRequisicao.lista(
                                        "paymentMethod",
                                        params.getPaymentMethod(),
                                        PaymentMethod::fromLabel)),
                        doCliente(params.getClientId()),
                        FinanceSpecifications.<ClientPayment>textoEm(
                                params.getSearchTerm(), "description", "client.fullName"));

        // O caso de uso principal é cobrança: o que vence primeiro aparece primeiro.
        var pageable =
                PageRequests.of(
                        params.getPageNumber(),
                        params.getPageSize(),
                        Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by("createdAt")));
        return repository.findAll(spec, pageable).map(PaymentService::toDTO);
    }

    public FinanceSummaryDTO summary(PaymentSearchParams params) {
        RecorteDeData.exigirUmRecorte(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Map<String, Object> valores = new HashMap<>();
        StringBuilder where = new StringBuilder(SOMENTE_ATIVOS);
        String campo =
                RecorteDeData.ehCaixa(params.getPaidFrom(), params.getPaidTo())
                        ? "x.paidDate"
                        : "x.dueDate";
        LocalDate de = params.getPaidFrom() != null ? params.getPaidFrom() : params.getDueFrom();
        LocalDate ate = params.getPaidTo() != null ? params.getPaidTo() : params.getDueTo();
        if (de != null) {
            where.append(" AND ").append(campo).append(" >= :de");
            valores.put("de", de);
        }
        if (ate != null) {
            where.append(" AND ").append(campo).append(" <= :ate");
            valores.put("ate", ate);
        }
        if (params.getClientId() != null) {
            where.append(" AND x.client.id = :clienteId");
            valores.put("clienteId", params.getClientId());
        }
        return consultas.resumo(ENTIDADE, where.toString(), valores);
    }

    public List<FinanceTimelinePointDTO> timeline(
            LocalDate de, LocalDate ate, boolean porPagamento) {
        return consultas.serieMensal(ENTIDADE, SOMENTE_ATIVOS, Map.of(), de, ate, porPagamento);
    }

    private static Specification<ClientPayment> ativos() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<ClientPayment> doCliente(UUID clientId) {
        return (root, query, cb) ->
                clientId == null ? null : cb.equal(root.get("client").get("id"), clientId);
    }

    private static PaymentListItemDTO toDTO(ClientPayment entity) {
        PaymentListItemDTO dto = new PaymentListItemDTO();
        dto.setId(entity.getId());
        if (entity.getClient() != null) {
            dto.setClientId(entity.getClient().getId());
            dto.setClientName(entity.getClient().getFullName());
        }
        dto.setDescription(entity.getDescription());
        dto.setAmount(entity.getAmount());
        dto.setInstallmentNumber(entity.getInstallmentNumber());
        dto.setInstallmentTotal(entity.getInstallmentTotal());
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setPaymentMethod(
                entity.getPaymentMethod() != null ? entity.getPaymentMethod().getLabel() : null);
        dto.setNotes(entity.getNotes());
        dto.setOverdue(RegrasDeVencimento.estaAtrasado(entity.getStatus(), entity.getDueDate()));
        return dto;
    }
}
