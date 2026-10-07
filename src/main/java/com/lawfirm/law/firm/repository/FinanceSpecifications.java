package com.lawfirm.law.firm.repository;

import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

/**
 * Filtros que valem igual para pagamentos, despesas e receitas.
 *
 * <p>As três listas têm os mesmos recortes — período por vencimento, período por pagamento, status,
 * forma de pagamento — porque respondem às mesmas perguntas sobre dinheiro. Escrever três vezes o
 * mesmo {@code between} é como um deles acaba inclusivo numa ponta e exclusivo na outra.
 *
 * <p>Genéricos em {@code <T>}: o que muda entre as três é a entidade, não o predicado. Os nomes de
 * campo ({@code dueDate}, {@code paidDate}, {@code status}) são iguais nas três de propósito.
 */
public final class FinanceSpecifications {

    private FinanceSpecifications() {}

    /** Intervalo de vencimento, inclusivo nas duas pontas. Nulos de um lado viram aberto. */
    public static <T> Specification<T> dueBetween(LocalDate de, LocalDate ate) {
        return entre("dueDate", de, ate);
    }

    /** Intervalo de data de pagamento, inclusivo. Lançamento não pago nunca entra. */
    public static <T> Specification<T> paidBetween(LocalDate de, LocalDate ate) {
        return entre("paidDate", de, ate);
    }

    private static <T> Specification<T> entre(String campo, LocalDate de, LocalDate ate) {
        return (root, query, cb) -> {
            if (de == null && ate == null) return null;
            var path = root.<LocalDate>get(campo);
            if (de != null && ate != null) return cb.between(path, de, ate);
            if (de != null) return cb.greaterThanOrEqualTo(path, de);
            return cb.lessThanOrEqualTo(path, ate);
        };
    }

    public static <T> Specification<T> statusIn(List<PaymentStatus> valores) {
        return (root, query, cb) ->
                CollectionUtils.isEmpty(valores) ? null : root.get("status").in(valores);
    }

    public static <T> Specification<T> methodIn(List<PaymentMethod> valores) {
        return (root, query, cb) ->
                CollectionUtils.isEmpty(valores) ? null : root.get("paymentMethod").in(valores);
    }

    /**
     * A ordem das três listas: vencido, a vencer, cancelado, pago.
     *
     * <p>Vem como {@code Specification} e não como {@code Sort} porque "vencido" não é valor de
     * coluna: é {@code Pendente} com vencimento no passado, e {@code Sort} só sabe nomear campos. O
     * {@code CASE} abaixo ordena pelos três status que existem no banco e deixa o {@code dueDate}
     * fazer o resto — dentro de Pendente, o que venceu tem data menor e sobe sozinho. Vencido antes
     * de a vencer sai de graça, sem um segundo critério.
     *
     * <p>É também por isso que aqui não aparece {@code CURRENT_DATE}. Se aparecesse, o SQL cortaria
     * o atraso pelo dia do banco enquanto {@link com.lawfirm.law.firm.util.DueDateRules#isOverdue}
     * corta pelo dia do escritório: das 21h à meia-noite de Brasília a linha voltaria {@code
     * overdue=true} ordenada no balde de quem ainda não venceu.
     *
     * <p>O {@code createdAt} desempata. Sem ele, lançamentos do mesmo vencimento saem na ordem que
     * o banco escolher, e a mesma linha pode aparecer na página 1 e faltar na 2.
     */
    public static <T> Specification<T> orderByStatusThenDueDate() {
        return (root, query, cb) -> {
            // A consulta de contagem que o Pageable dispara não tem ORDER BY, e alguns bancos
            // recusam ordenar por coluna fora do SELECT de um count.
            if (isCountQuery(query)) return null;
            query.orderBy(
                    cb.asc(statusRank(root, cb)),
                    cb.asc(root.get("dueDate")),
                    cb.asc(root.get("createdAt")));
            return null;
        };
    }

    /** Pendente (onde vive o vencido) primeiro, Pago por último — Cancelado entre os dois. */
    private static <T> Expression<Integer> statusRank(Root<T> root, CriteriaBuilder cb) {
        return cb.<PaymentStatus, Integer>selectCase(root.<PaymentStatus>get("status"))
                .when(PaymentStatus.PENDENTE, 0)
                .when(PaymentStatus.CANCELADO, 1)
                .otherwise(2);
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> tipo = query.getResultType();
        return tipo == Long.class || tipo == long.class;
    }

    /**
     * Busca textual sem acento e sem diferenciar maiúscula, sobre um ou mais campos.
     *
     * <p>A normalização acontece no termo digitado e, no banco, por {@code unaccent} não estar
     * garantido, comparando em minúsculas contra o valor como está. É a mesma escolha do filtro de
     * clientes: procurar "Jose" achar "José" exige a coluna normalizada, e isso é migration própria
     * — enquanto não houver, o acento importa e está registrado aqui em vez de surpreender.
     */
    public static <T> Specification<T> textoEm(String termo, String... campos) {
        return (root, query, cb) -> {
            if (termo == null || termo.isBlank() || campos.length == 0) return null;
            String alvo = "%" + semAcento(termo.trim().toLowerCase()) + "%";
            var predicados = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            for (String campo : campos) {
                Expression<String> path = caminho(root, campo);
                predicados.add(cb.like(cb.lower(path), alvo));
            }
            return cb.or(predicados.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    /** Resolve "client.fullName" como join, e "description" como campo direto. */
    @SuppressWarnings("unchecked")
    private static <T> Expression<String> caminho(
            jakarta.persistence.criteria.Root<T> root, String campo) {
        if (!campo.contains(".")) return root.get(campo);
        String[] partes = campo.split("\\.", 2);
        return ((jakarta.persistence.criteria.Join<Object, Object>)
                        root.join(partes[0], jakarta.persistence.criteria.JoinType.LEFT))
                .get(partes[1]);
    }

    private static String semAcento(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
