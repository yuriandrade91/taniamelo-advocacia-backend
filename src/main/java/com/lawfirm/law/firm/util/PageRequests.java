package com.lawfirm.law.firm.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Traduz a paginação do contrato (1-based, como a API expõe) para o {@link Pageable} do Spring
 * (0-based), aplicando os limites.
 *
 * <p>Existe por dois motivos. O primeiro é que a mesma normalização estava copiada em sete
 * services, e a sétima cópia é a que ia divergir. O segundo é o teto: {@code pageSize} não tinha
 * limite, e {@code ?pageSize=100000} devolvia a tabela inteira numa requisição - na rota mais
 * chamada da aplicação, com a base do escritório real do outro lado.
 *
 * <p>O teto <b>corta em silêncio</b> em vez de recusar com 400. Pedir mais do que cabe não é erro
 * de quem chama, e o bloco {@code pagination} da resposta devolve o tamanho realmente usado - quem
 * consome descobre pela própria resposta, sem precisar tratar mais um caso de erro.
 */
public final class PageRequests {

    /** Página usada quando o cliente não informa nada. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * Máximo de registros por página.
     *
     * <p>100 é o suficiente para qualquer tela do sistema (a maior lista carrega 50) e mantém a
     * resposta em ordem de dezenas de KB. Aumentar isto é decisão consciente, não parâmetro de
     * query.
     */
    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {}

    /**
     * Pageable com a ordenação pedida, <b>mais o id como último critério</b>.
     *
     * <p>O desempate não é detalhe: ordenar a listagem de clientes só por {@code updatedAt} deixa
     * dois registros do mesmo instante na ordem que o banco escolher, e ela pode mudar entre a
     * consulta da página 1 e a da página 2 — a mesma linha aparece duas vezes e outra nunca
     * aparece. Com um critério único no fim, a ordem é total e a paginação, estável.
     *
     * <p>Vale para toda consulta JPA que passa por aqui: o campo {@code id} existe em todas as
     * entidades (PK em UUID). Já quem ordena pelo próprio {@code @Query} nativo ou por {@code
     * Specification} usa a sobrecarga sem {@code Sort} e carrega o desempate no próprio ORDER BY —
     * ordenação no Pageable substituiria a de lá.
     */
    public static Pageable of(int pageNumber, int pageSize, Sort sort) {
        return PageRequest.of(
                normalizePage(pageNumber), normalizeSize(pageSize), comDesempate(sort));
    }

    /** Acrescenta {@code id} ao fim, salvo quem já o declarou. */
    private static Sort comDesempate(Sort sort) {
        boolean jaTem = sort.stream().anyMatch(order -> "id".equals(order.getProperty()));
        return jaTem ? sort : sort.and(Sort.by(Sort.Order.asc("id")));
    }

    public static Pageable of(int pageNumber, int pageSize) {
        return PageRequest.of(normalizePage(pageNumber), normalizeSize(pageSize));
    }

    /** 1-based no contrato, 0-based no Spring. Página zero ou negativa cai na primeira. */
    private static int normalizePage(int pageNumber) {
        return Math.max(0, pageNumber - 1);
    }

    /** Ausente ou inválido vira o default; acima do teto, é cortado no teto. */
    private static int normalizeSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
