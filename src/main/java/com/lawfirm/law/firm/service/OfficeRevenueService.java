package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueListItemDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueRequestDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueResponseDTO;
import com.lawfirm.law.firm.dto.OfficeRevenueSearchParams;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientPayment;
import com.lawfirm.law.firm.model.OfficeRevenue;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.ClientPaymentRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.repository.FinanceSpecifications;
import com.lawfirm.law.firm.repository.OfficeRevenueRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.util.PageRequests;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entradas de caixa do escritório: honorário, parecer avulso, reembolso. */
@Service
public class OfficeRevenueService {

    private static final String ENTIDADE = "OfficeRevenue";

    private final OfficeRevenueRepository repository;
    private final ClientRepository clientRepository;
    private final ClientPaymentRepository clientPaymentRepository;
    private final FinanceQueries queries;

    public OfficeRevenueService(
            OfficeRevenueRepository repository,
            ClientRepository clientRepository,
            ClientPaymentRepository clientPaymentRepository,
            FinanceQueries queries) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.clientPaymentRepository = clientPaymentRepository;
        this.queries = queries;
    }

    @Transactional
    public OfficeRevenueResponseDTO create(OfficeRevenueRequestDTO dto) {
        OfficeRevenue entity = new OfficeRevenue();
        aplicar(entity, dto);
        entity.setCreatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<OfficeRevenueListItemDTO> list(OfficeRevenueSearchParams params) {
        PeriodBasis.requireSingleRange(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Specification<OfficeRevenue> spec =
                Specification.allOf(
                        FinanceSpecifications.<OfficeRevenue>dueBetween(
                                params.getDueFrom(), params.getDueTo()),
                        FinanceSpecifications.<OfficeRevenue>paidBetween(
                                params.getPaidFrom(), params.getPaidTo()),
                        FinanceSpecifications.<OfficeRevenue>statusIn(
                                RequestEnums.list(
                                        "status", params.getStatus(), PaymentStatus::fromLabel)),
                        FinanceSpecifications.<OfficeRevenue>methodIn(
                                RequestEnums.list(
                                        "paymentMethod",
                                        params.getPaymentMethod(),
                                        PaymentMethod::fromLabel)),
                        doCliente(params.getClientId()),
                        FinanceSpecifications.<OfficeRevenue>textoEm(
                                params.getSearchTerm(), "description", "client.fullName"),
                        FinanceSpecifications.<OfficeRevenue>orderByStatusThenDueDate());

        // A ordem (vencido, a vencer, cancelado, pago) vem da especificação, porque depende de um
        // CASE sobre o status. Ordenação no Pageable substituiria a dela — por isso não há nenhuma.
        var pageable = PageRequests.of(params.getPageNumber(), params.getPageSize());
        return repository.findAll(spec, pageable).map(OfficeRevenueService::toListItem);
    }

    @Transactional(readOnly = true)
    public OfficeRevenueResponseDTO get(UUID id) {
        return toDTO(buscarOuFalhar(id));
    }

    @Transactional
    public OfficeRevenueResponseDTO update(UUID id, OfficeRevenueRequestDTO dto) {
        OfficeRevenue entity = buscarOuFalhar(id);
        aplicar(entity, dto);
        entity.setUpdatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        OfficeRevenue entity = buscarOuFalhar(id);
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(CurrentUser.id());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public FinanceSummaryDTO summary(OfficeRevenueSearchParams params) {
        PeriodBasis.requireSingleRange(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Map<String, Object> valores = new HashMap<>();
        StringBuilder where = new StringBuilder("1 = 1");
        String campo =
                PeriodBasis.isCashBasis(params.getPaidFrom(), params.getPaidTo())
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
        return queries.summary(ENTIDADE, where.toString(), valores);
    }

    @Transactional(readOnly = true)
    public List<FinanceTimelinePointDTO> timeline(
            LocalDate de, LocalDate ate, boolean porPagamento) {
        return queries.monthlySeries(ENTIDADE, null, Map.of(), de, ate, porPagamento);
    }

    private static Specification<OfficeRevenue> doCliente(UUID clientId) {
        return (root, query, cb) ->
                clientId == null ? null : cb.equal(root.get("client").get("id"), clientId);
    }

    private OfficeRevenue buscarOuFalhar(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Receita não encontrada: " + id));
    }

    private void aplicar(OfficeRevenue entity, OfficeRevenueRequestDTO dto) {
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setDueDate(dto.getDueDate());
        entity.setNotes(dto.getNotes());
        entity.setPaymentMethod(
                RequestEnums.single(
                        "paymentMethod", dto.getPaymentMethod(), PaymentMethod::fromLabel));

        PaymentStatus status =
                RequestEnums.single("status", dto.getStatus(), PaymentStatus::fromLabel);
        entity.setStatus(status != null ? status : PaymentStatus.PENDENTE);
        entity.setPaidDate(DueDateRules.resolvePaidDate(entity.getStatus(), dto.getPaidDate()));

        entity.setClient(resolverCliente(dto.getClientId()));
        entity.setSourcePayment(resolverParcela(dto.getSourcePaymentId()));
    }

    /**
     * Cliente informado precisa existir.
     *
     * <p>Aceitar um id qualquer gravaria a receita sem vínculo e sem aviso: a linha apareceria na
     * Carteira com o cliente em branco, e descobrir de quem era exigiria o log.
     */
    private Client resolverCliente(UUID clientId) {
        if (clientId == null) return null;
        return clientRepository
                .findById(clientId)
                .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + clientId));
    }

    private ClientPayment resolverParcela(UUID paymentId) {
        if (paymentId == null) return null;
        return clientPaymentRepository
                .findById(paymentId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Pagamento de origem não encontrado: " + paymentId));
    }

    /** A projeção da grade. O detalhe tem a sua em {@link #toDTO}, e é de propósito. */
    private static OfficeRevenueListItemDTO toListItem(OfficeRevenue entity) {
        OfficeRevenueListItemDTO dto = new OfficeRevenueListItemDTO();
        dto.setId(entity.getId());
        dto.setDescription(entity.getDescription());
        if (entity.getClient() != null) {
            dto.setClientName(entity.getClient().getFullName());
        }
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setOverdue(DueDateRules.isOverdue(entity.getStatus(), entity.getDueDate()));
        return dto;
    }

    private OfficeRevenueResponseDTO toDTO(OfficeRevenue entity) {
        OfficeRevenueResponseDTO dto = new OfficeRevenueResponseDTO();
        dto.setId(entity.getId());
        dto.setDescription(entity.getDescription());
        dto.setAmount(entity.getAmount());
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setPaymentMethod(
                entity.getPaymentMethod() != null ? entity.getPaymentMethod().getLabel() : null);
        if (entity.getClient() != null) {
            dto.setClientId(entity.getClient().getId());
            dto.setClientName(entity.getClient().getFullName());
        }
        if (entity.getSourcePayment() != null) {
            dto.setSourcePaymentId(entity.getSourcePayment().getId());
        }
        dto.setNotes(entity.getNotes());
        dto.setOverdue(DueDateRules.isOverdue(entity.getStatus(), entity.getDueDate()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
