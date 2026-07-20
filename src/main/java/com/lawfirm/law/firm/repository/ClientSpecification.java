package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.util.EnumLabelSupport;
import jakarta.persistence.criteria.Expression;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros da listagem de clientes. A busca textual usa a extensão unaccent do PostgreSQL (garantida
 * pela migration V1) para comparação sem acento nos dois lados.
 */
public final class ClientSpecification {

    private ClientSpecification() {}

    public static Specification<Client> searchTerm(String searchTerm) {
        return (root, query, cb) -> {
            if (searchTerm == null || searchTerm.isBlank()) return null;

            String normalized = EnumLabelSupport.normalize(searchTerm.trim());
            String term = "%" + normalized + "%";

            Expression<String> cpfExpr =
                    cb.function("unaccent", String.class, cb.lower(root.get("cpf")));
            Expression<String> fullNameExpr =
                    cb.function("unaccent", String.class, cb.lower(root.get("fullName")));

            return cb.or(cb.like(cpfExpr, term), cb.like(fullNameExpr, term));
        };
    }

    public static Specification<Client> benefitIn(List<BenefitType> benefits) {
        return (root, query, cb) ->
                (benefits == null || benefits.isEmpty()) ? null : root.get("benefit").in(benefits);
    }

    public static Specification<Client> situationIn(List<Situation> situations) {
        return (root, query, cb) ->
                (situations == null || situations.isEmpty())
                        ? null
                        : root.get("situation").in(situations);
    }

    public static Specification<Client> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            var path = root.<Instant>get("createdAt");
            if (from != null && to != null) return cb.between(path, from, to);
            if (from != null) return cb.greaterThanOrEqualTo(path, from);
            return cb.lessThanOrEqualTo(path, to);
        };
    }

    public static Specification<Client> combine(List<Specification<Client>> specs) {
        Specification<Client> result = null;
        for (Specification<Client> s : specs) {
            if (s == null) continue;
            result = (result == null) ? s : result.and(s);
        }
        return result;
    }
}
