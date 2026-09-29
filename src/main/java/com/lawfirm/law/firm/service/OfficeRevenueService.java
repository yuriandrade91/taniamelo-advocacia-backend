package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
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
import org.springframework.data.domain.Sort;
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
    private final ConsultasFinanceiras consultas;

    public OfficeRevenueService(
            OfficeRevenueRepository repository,
            ClientRepository clientRepository,
            ClientPaymentRepository clientPaymentRepository,
            ConsultasFinanceiras consultas) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.clientPaymentRepository = clientPaymentRepository;
        this.consultas = consultas;
    }

    @Transactional
    public OfficeRevenueResponseDTO create(OfficeRevenueRequestDTO dto) {
        OfficeRevenue entity = new OfficeRevenue();
        aplicar(entity, dto);
        entity.setCreatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    public Page<OfficeRevenueResponseDTO> list(OfficeRevenueSearchParams params) {
        RecorteDeData.exigirUmRecorte(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Specification<OfficeRevenue> spec =
                Specification.allOf(
                        FinanceSpecifications.<OfficeRevenue>dueBetween(
                                params.getDueFrom(), params.getDueTo()),
                        FinanceSpecifications.<OfficeRevenue>paidBetween(
                                params.getPaidFrom(), params.getPaidTo()),
                        FinanceSpecifications.<OfficeRevenue>statusIn(
                                EnumsDeRequisicao.lista(
                                        "status", params.getStatus(), PaymentStatus::fromLabel)),
                        FinanceSpecifications.<OfficeRevenue>methodIn(
                                EnumsDeRequisicao.lista(
                                        "paymentMethod",
                                        params.getPaymentMethod(),
                                        PaymentMethod::fromLabel)),
                        doCliente(params.getClientId()),
                        FinanceSpecifications.<OfficeRevenue>textoEm(
                                params.getSearchTerm(), "description", "client.fullName"));

        var pageable =
                PageRequests.of(
                        params.getPageNumber(),
                        params.getPageSize(),
                        Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by("createdAt")));
        return repository.findAll(spec, pageable).map(this::toDTO);
    }

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

    public FinanceSummaryDTO summary(OfficeRevenueSearchParams params) {
        RecorteDeData.exigirUmRecorte(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Map<String, Object> valores = new HashMap<>();
        StringBuilder where = new StringBuilder("1 = 1");
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
        return consultas.serieMensal(ENTIDADE, null, Map.of(), de, ate, porPagamento);
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
                EnumsDeRequisicao.unico(
                        "paymentMethod", dto.getPaymentMethod(), PaymentMethod::fromLabel));

        PaymentStatus status =
                EnumsDeRequisicao.unico("status", dto.getStatus(), PaymentStatus::fromLabel);
        entity.setStatus(status != null ? status : PaymentStatus.PENDENTE);
        entity.setPaidDate(
                RegrasDeVencimento.dataDePagamento(entity.getStatus(), dto.getPaidDate()));

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
        dto.setOverdue(RegrasDeVencimento.estaAtrasado(entity.getStatus(), entity.getDueDate()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
