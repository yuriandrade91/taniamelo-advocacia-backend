package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.config.DatabaseFeatures;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.Situation;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public final class ClientSpecification {

    private static final String ACCENTED = "ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ";
    private static final String UNACCENTED = "AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn";

    private ClientSpecification() {}

    public static Specification<Client> searchTerm(String searchTerm) {
        return (root, query, cb) -> {
            if (searchTerm == null || searchTerm.isBlank()) return null;

            // Normalize the incoming term: remove diacritics and lower-case (JVM side)
            String normalized = Normalizer.normalize(searchTerm.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            String term = "%" + normalized + "%";

            Expression<String> cpfExpr;
            Expression<String> fullNameExpr;

            if (DatabaseFeatures.isUnaccentAvailable()) {
                // Use database unaccent function on the fields (PostgreSQL unaccent extension)
                cpfExpr = cb.function("unaccent", String.class, cb.lower(root.get("cpf")));
                fullNameExpr = cb.function("unaccent", String.class, cb.lower(root.get("fullName")));
            } else {
                // Fallback: use translate to strip common accents in SQL (Postgres has translate)
                // translate(lower(field), accents, replacements)
                cpfExpr = cb.function("translate", String.class,
                        cb.lower(root.get("cpf")), cb.literal(ACCENTED), cb.literal(UNACCENTED));
                fullNameExpr = cb.function("translate", String.class,
                        cb.lower(root.get("fullName")), cb.literal(ACCENTED), cb.literal(UNACCENTED));
            }

            return cb.or(
                    cb.like(cpfExpr, term),
                    cb.like(fullNameExpr, term)
            );
        };
    }

    public static Specification<Client> benefitIn(List<BenefitType> benefits) {
        return (root, query, cb) -> {
            if (benefits == null || benefits.isEmpty()) return null;
            Expression<BenefitType> benefitExpr = root.get("benefit");
            return benefitExpr.in(benefits);
        };
    }

    public static Specification<Client> situationIn(List<Situation> situations) {
        return (root, query, cb) -> {
            if (situations == null || situations.isEmpty()) return null;
            Expression<Situation> situationExpr = root.get("situation");
            return situationExpr.in(situations);
        };
    }

    public static Specification<Client> createdBetween(java.time.Instant from, java.time.Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            // createdAt column is Instant in entity; use between or >=/<= depending on nulls
            var path = root.get("createdAt");
            if (from != null && to != null) {
                return cb.between(path.as(java.time.Instant.class), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(path.as(java.time.Instant.class), from);
            } else {
                return cb.lessThanOrEqualTo(path.as(java.time.Instant.class), to);
            }
        };
    }

    public static Specification<Client> combine(List<Specification<Client>> specs) {
        Specification<Client> result = null;
        for (Specification<Client> s : specs) {
            if (s == null) continue;
            result = (result == null) ? Specification.where(s) : result.and(s);
        }
        return result;
    }
}
