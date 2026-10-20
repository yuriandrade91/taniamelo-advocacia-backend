package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.FinanceSearchParams;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.repository.FinanceSpecifications;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros que pagamentos, despesas e receitas têm em comum (ver {@link FinanceSearchParams}).
 *
 * <p>Os dois métodos recusam competência e caixa na mesma chamada antes de montar qualquer coisa:
 * quem os usa não tem como esquecer a validação de {@link PeriodBasis}.
 */
public final class FinanceFilters {

    private FinanceFilters() {}

    /** Período (vencimento ou pagamento), situação e forma de pagamento, para a lista. */
    public static <T> Specification<T> common(FinanceSearchParams params) {
        requireSingleRange(params);
        return Specification.allOf(
                FinanceSpecifications.<T>dueBetween(params.getDueFrom(), params.getDueTo()),
                FinanceSpecifications.<T>paidBetween(params.getPaidFrom(), params.getPaidTo()),
                FinanceSpecifications.<T>statusIn(
                        RequestEnums.list("status", params.getStatus(), PaymentStatus::fromLabel)),
                FinanceSpecifications.<T>methodIn(
                        RequestEnums.list(
                                "paymentMethod",
                                params.getPaymentMethod(),
                                PaymentMethod::fromLabel)));
    }

    /**
     * Predicado JPQL (alias {@code x}) do recorte de período para o resumo, a partir de {@code
     * base}. Os parâmetros nomeados que ele usa são postos em {@code valores}.
     */
    public static StringBuilder period(
            String base, FinanceSearchParams params, Map<String, Object> valores) {
        requireSingleRange(params);
        StringBuilder where = new StringBuilder(base);
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
        return where;
    }

    private static void requireSingleRange(FinanceSearchParams params) {
        PeriodBasis.requireSingleRange(
                params.getDueFrom(), params.getDueTo(), params.getPaidFrom(), params.getPaidTo());
    }
}
