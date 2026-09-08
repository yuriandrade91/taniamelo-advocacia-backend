package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.Appointment;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

/** Filtros da listagem da agenda (compromissos). Segue o padrão de {@code ClientSpecification}. */
public final class AppointmentSpecification {

    private AppointmentSpecification() {}

    /** Só compromissos não excluídos (soft delete). */
    public static Specification<Appointment> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Appointment> startBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            var path = root.<Instant>get("startAt");
            if (from != null && to != null) return cb.between(path, from, to);
            if (from != null) return cb.greaterThanOrEqualTo(path, from);
            return cb.lessThanOrEqualTo(path, to);
        };
    }

    /**
     * Compromissos que se sobrepõem à janela {@code [from, to)}.
     *
     * <p>A regra é {@code existente.startAt < to AND existente.endAt > from}: intervalos
     * <b>semiabertos</b>. Isso faz 14h-15h e 15h-16h <b>não</b> conflitarem — encostar não é
     * sobrepor, e tratar como conflito acusaria toda agenda cheia mas correta.
     *
     * <p>Nulos devolvem {@code null} (filtro ignorado) em vez de uma janela infinita, que traria a
     * agenda inteira como conflito.
     */
    public static Specification<Appointment> overlaps(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null || to == null) return null;
            return cb.and(
                    cb.lessThan(root.get("startAt"), to), cb.greaterThan(root.get("endAt"), from));
        };
    }

    /**
     * Exclui um id do resultado.
     *
     * <p>Existe para a checagem de conflito na <b>edição</b>: sem isso, todo compromisso
     * conflitaria consigo mesmo e a tela avisaria em cima de uma alteração que não mudou horário
     * nenhum.
     */
    public static Specification<Appointment> idNot(UUID id) {
        return (root, query, cb) -> id == null ? null : cb.notEqual(root.get("id"), id);
    }

    /** Tipo dentro de uma lista (aceita 1 ou N valores - filtro vazio/nulo é ignorado). */
    public static Specification<Appointment> typeIn(List<AppointmentType> types) {
        return (root, query, cb) ->
                CollectionUtils.isEmpty(types) ? null : root.get("type").in(types);
    }

    /** Situação dentro de uma lista (aceita 1 ou N valores - filtro vazio/nulo é ignorado). */
    public static Specification<Appointment> statusIn(List<AppointmentStatus> statuses) {
        return (root, query, cb) ->
                CollectionUtils.isEmpty(statuses) ? null : root.get("status").in(statuses);
    }

    /**
     * Ano(s) de início dentro de uma lista, via {@code date_part('year', start_at)} (Postgres) -
     * aceita 1 ou N valores. Combinar com {@link #monthsIn} para o filtro de mês independente do
     * ano.
     */
    public static Specification<Appointment> yearsIn(List<Integer> years) {
        return (root, query, cb) -> {
            if (CollectionUtils.isEmpty(years)) return null;
            Expression<Double> yearExpr =
                    cb.function(
                            "date_part",
                            Double.class,
                            cb.literal("year"),
                            root.<Instant>get("startAt"));
            return yearExpr.in(years.stream().map(Integer::doubleValue).toList());
        };
    }

    /**
     * Mês(es) 1-12 de início dentro de uma lista, via {@code date_part('month', start_at)}
     * (Postgres) - aceita 1 ou N valores.
     */
    public static Specification<Appointment> monthsIn(List<Integer> months) {
        return (root, query, cb) -> {
            if (CollectionUtils.isEmpty(months)) return null;
            Expression<Double> monthExpr =
                    cb.function(
                            "date_part",
                            Double.class,
                            cb.literal("month"),
                            root.<Instant>get("startAt"));
            return monthExpr.in(months.stream().map(Integer::doubleValue).toList());
        };
    }

    public static Specification<Appointment> clientIs(UUID clientId) {
        return (root, query, cb) ->
                clientId == null ? null : cb.equal(root.get("clientId"), clientId);
    }

    /** Busca por título, sem acento/caixa (mesma técnica de {@code ClientSpecification}). */
    public static Specification<Appointment> titleContains(String searchTerm) {
        return (root, query, cb) -> {
            if (searchTerm == null || searchTerm.isBlank()) return null;
            String folded =
                    Normalizer.normalize(searchTerm.trim().toLowerCase(), Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");
            Expression<String> titleExpr =
                    cb.function("unaccent", String.class, cb.lower(root.get("title")));
            return cb.like(titleExpr, "%" + folded + "%");
        };
    }

    public static Specification<Appointment> combine(List<Specification<Appointment>> specs) {
        Specification<Appointment> result = null;
        for (Specification<Appointment> s : specs) {
            if (s == null) continue;
            result = (result == null) ? s : result.and(s);
        }
        return result;
    }
}
