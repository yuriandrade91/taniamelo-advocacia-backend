package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.CategoryAmountDTO;
import com.lawfirm.law.firm.dto.FinanceSummaryDTO;
import com.lawfirm.law.firm.dto.FinanceTimelinePointDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseListItemDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseRequestDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseResponseDTO;
import com.lawfirm.law.firm.dto.OfficeExpenseSearchParams;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.ExpenseCategory;
import com.lawfirm.law.firm.model.OfficeExpense;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.FinanceSpecifications;
import com.lawfirm.law.firm.repository.OfficeExpenseRepository;
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

/** Saídas de caixa do escritório: lançar, listar, somar e distribuir pelos meses. */
@Service
public class OfficeExpenseService {

    /**
     * Nome JPQL da entidade, para {@link FinanceQueries}. Constante do código — nunca chega aqui
     * vindo de uma requisição.
     */
    private static final String ENTIDADE = "OfficeExpense";

    /** Bordas usadas quando a consulta não recorta período. Datas reais, nunca nulo. */
    private static final LocalDate SEM_INICIO = LocalDate.of(1900, 1, 1);

    private static final LocalDate SEM_FIM = LocalDate.of(2999, 12, 31);

    private final OfficeExpenseRepository repository;
    private final FinanceQueries queries;

    public OfficeExpenseService(OfficeExpenseRepository repository, FinanceQueries queries) {
        this.repository = repository;
        this.queries = queries;
    }

    @Transactional
    public OfficeExpenseResponseDTO create(OfficeExpenseRequestDTO dto) {
        OfficeExpense entity = new OfficeExpense();
        aplicar(entity, dto);
        entity.setCreatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<OfficeExpenseListItemDTO> list(OfficeExpenseSearchParams params) {
        PeriodBasis.requireSingleRange(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Specification<OfficeExpense> spec =
                Specification.allOf(
                        FinanceSpecifications.<OfficeExpense>dueBetween(
                                params.getDueFrom(), params.getDueTo()),
                        FinanceSpecifications.<OfficeExpense>paidBetween(
                                params.getPaidFrom(), params.getPaidTo()),
                        FinanceSpecifications.<OfficeExpense>statusIn(
                                RequestEnums.list(
                                        "status", params.getStatus(), PaymentStatus::fromLabel)),
                        FinanceSpecifications.<OfficeExpense>methodIn(
                                RequestEnums.list(
                                        "paymentMethod",
                                        params.getPaymentMethod(),
                                        PaymentMethod::fromLabel)),
                        categoriaEm(params.getCategory()),
                        FinanceSpecifications.<OfficeExpense>textoEm(
                                params.getSearchTerm(), "description", "supplier"),
                        FinanceSpecifications.<OfficeExpense>orderByStatusThenDueDate());

        // A ordem (vencido, a vencer, cancelado, pago) vem da especificação, porque depende de um
        // CASE sobre o status. Ordenação no Pageable substituiria a dela — por isso não há nenhuma.
        var pageable = PageRequests.of(params.getPageNumber(), params.getPageSize());
        return repository.findAll(spec, pageable).map(OfficeExpenseService::toListItem);
    }

    @Transactional(readOnly = true)
    public OfficeExpenseResponseDTO get(UUID id) {
        return toDTO(buscarOuFalhar(id));
    }

    @Transactional
    public OfficeExpenseResponseDTO update(UUID id, OfficeExpenseRequestDTO dto) {
        OfficeExpense entity = buscarOuFalhar(id);
        aplicar(entity, dto);
        entity.setUpdatedBy(CurrentUser.id());
        return toDTO(repository.save(entity));
    }

    /**
     * Exclusão lógica.
     *
     * <p>Despesa apagada de vez levaria junto o histórico do mês fechado, e resultado de mês
     * fechado não muda porque alguém errou o lançamento — corrige-se com outro lançamento.
     */
    @Transactional
    public void delete(UUID id) {
        OfficeExpense entity = buscarOuFalhar(id);
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(CurrentUser.id());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public FinanceSummaryDTO summary(OfficeExpenseSearchParams params) {
        PeriodBasis.requireSingleRange(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());

        Map<String, Object> valores = new HashMap<>();
        String where = recorte(params, valores);

        FinanceSummaryDTO resumo = queries.summary(ENTIDADE, where, valores);

        // A composição do gasto olha sempre o que FOI PAGO, no recorte de caixa. Com a consulta
        // por vencimento, o período do gráfico é o mesmo intervalo lido como data de pagamento —
        // é a única leitura que responde "no que gastamos".
        LocalDate de = params.getPaidFrom() != null ? params.getPaidFrom() : params.getDueFrom();
        LocalDate ate = params.getPaidTo() != null ? params.getPaidTo() : params.getDueTo();
        // Sem recorte, a pergunta é "no que gastamos até hoje" — e a consulta precisa de datas de
        // verdade nas bordas, não de nulo (ver o comentário em somarPorCategoria).
        List<CategoryAmountDTO> porCategoria =
                repository.somarPorCategoria(
                        de != null ? de : SEM_INICIO, ate != null ? ate : SEM_FIM);
        resumo.setByCategory(porCategoria);
        return resumo;
    }

    @Transactional(readOnly = true)
    public List<FinanceTimelinePointDTO> timeline(
            LocalDate de, LocalDate ate, boolean porPagamento) {
        return queries.monthlySeries(ENTIDADE, null, Map.of(), de, ate, porPagamento);
    }

    private static Specification<OfficeExpense> categoriaEm(List<String> brutos) {
        List<ExpenseCategory> categorias =
                RequestEnums.list("category", brutos, ExpenseCategory::fromLabel);
        return (root, query, cb) ->
                categorias == null || categorias.isEmpty()
                        ? null
                        : root.get("category").in(categorias);
    }

    /** Monta o predicado de período para o resumo, no recorte que veio na requisição. */
    private static String recorte(OfficeExpenseSearchParams params, Map<String, Object> valores) {
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
        return where.toString();
    }

    private OfficeExpense buscarOuFalhar(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Despesa não encontrada: " + id));
    }

    private void aplicar(OfficeExpense entity, OfficeExpenseRequestDTO dto) {
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setCategory(
                RequestEnums.single("category", dto.getCategory(), ExpenseCategory::fromLabel));
        entity.setSupplier(dto.getSupplier());
        entity.setDueDate(dto.getDueDate());
        entity.setNotes(dto.getNotes());
        entity.setPaymentMethod(
                RequestEnums.single(
                        "paymentMethod", dto.getPaymentMethod(), PaymentMethod::fromLabel));

        PaymentStatus status =
                RequestEnums.single("status", dto.getStatus(), PaymentStatus::fromLabel);
        entity.setStatus(status != null ? status : PaymentStatus.PENDENTE);
        entity.setPaidDate(DueDateRules.resolvePaidDate(entity.getStatus(), dto.getPaidDate()));
    }

    /** A projeção da grade. O detalhe tem a sua em {@link #toDTO}, e é de propósito. */
    private static OfficeExpenseListItemDTO toListItem(OfficeExpense entity) {
        OfficeExpenseListItemDTO dto = new OfficeExpenseListItemDTO();
        dto.setId(entity.getId());
        dto.setDescription(entity.getDescription());
        dto.setSupplier(entity.getSupplier());
        dto.setCategory(entity.getCategory() != null ? entity.getCategory().getLabel() : null);
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setAmount(entity.getAmount());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setOverdue(DueDateRules.isOverdue(entity.getStatus(), entity.getDueDate()));
        return dto;
    }

    private OfficeExpenseResponseDTO toDTO(OfficeExpense entity) {
        OfficeExpenseResponseDTO dto = new OfficeExpenseResponseDTO();
        dto.setId(entity.getId());
        dto.setDescription(entity.getDescription());
        dto.setAmount(entity.getAmount());
        dto.setCategory(entity.getCategory() != null ? entity.getCategory().getLabel() : null);
        dto.setSupplier(entity.getSupplier());
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().getLabel() : null);
        dto.setPaymentMethod(
                entity.getPaymentMethod() != null ? entity.getPaymentMethod().getLabel() : null);
        dto.setNotes(entity.getNotes());
        dto.setOverdue(DueDateRules.isOverdue(entity.getStatus(), entity.getDueDate()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
