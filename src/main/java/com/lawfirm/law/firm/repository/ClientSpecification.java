package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Situation;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros da listagem de clientes. A busca textual usa a extensão unaccent do PostgreSQL (garantida
 * pela migration V1) para comparação sem acento nos dois lados.
 */
public final class ClientSpecification {

    private ClientSpecification() {}

    /**
     * Busca livre por nome completo ou CPF.
     *
     * <p>Nome: acento-fold + lowercase feito em Java preservando espaços/pontuação, casando com
     * {@code unaccent(lower(full_name))} do Postgres (que também preserva). Normalizar removendo
     * espaços/pontuação de um lado só faria nome com mais de uma palavra ("Maria da Silva") e CPF
     * formatado nunca casarem com o valor armazenado.
     *
     * <p>CPF: comparação por dígitos dos dois lados ({@code regexp_replace(cpf, '[^0-9]', '')}),
     * então "52998224725" encontra "529.982.247-25" e vice-versa, independente da formatação.
     */
    public static Specification<Client> searchTerm(String searchTerm) {
        return (root, query, cb) -> {
            if (searchTerm == null || searchTerm.isBlank()) return null;

            String folded =
                    Normalizer.normalize(searchTerm.trim().toLowerCase(), Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "");

            Expression<String> fullNameExpr =
                    cb.function("unaccent", String.class, cb.lower(root.get("fullName")));
            Predicate nameLike = cb.like(fullNameExpr, "%" + folded + "%");

            String digits = folded.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                return nameLike;
            }

            Expression<String> cpfDigits =
                    cb.function(
                            "regexp_replace",
                            String.class,
                            root.get("cpf"),
                            cb.literal("[^0-9]"),
                            cb.literal(""),
                            cb.literal("g"));
            Predicate cpfLike = cb.like(cpfDigits, "%" + digits + "%");

            return cb.or(nameLike, cpfLike);
        };
    }

    public static Specification<Client> benefitIn(List<BenefitType> benefits) {
        return (root, query, cb) -> {
            List<BenefitType> cleaned = withoutNulls(benefits);
            return cleaned.isEmpty() ? null : root.get("benefit").in(cleaned);
        };
    }

    public static Specification<Client> situationIn(List<Situation> situations) {
        return (root, query, cb) -> {
            List<Situation> cleaned = withoutNulls(situations);
            return cleaned.isEmpty() ? null : root.get("situation").in(cleaned);
        };
    }

    /**
     * Filtra por tipo de cliente (Verificado / Potencial).
     *
     * <p>Mesma forma de {@link #situationIn}: lista vazia devolve {@code null}, que o {@link
     * #combine} descarta. É o que faz "sem filtro" e "filtro com todos os valores" produzirem a
     * mesma query, em vez de um {@code IN ()} que o Postgres recusa.
     */
    public static Specification<Client> clientTypeIn(List<ClientType> clientTypes) {
        return (root, query, cb) -> {
            List<ClientType> cleaned = withoutNulls(clientTypes);
            return cleaned.isEmpty() ? null : root.get("clientType").in(cleaned);
        };
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

    /**
     * Remove nulos da lista de filtro. Um parâmetro de query vazio (ex.: {@code ?benefitType=}) é
     * convertido para {@code null} pelo conversor de enum e chega aqui como {@code [null]}; sem
     * essa limpeza viraria um {@code IN (null)} inválido/sem correspondência.
     */
    private static <T> List<T> withoutNulls(List<T> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
}
