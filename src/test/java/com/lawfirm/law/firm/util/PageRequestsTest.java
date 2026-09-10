package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@DisplayName("PageRequests: paginação 1-based do contrato -> Pageable do Spring, com teto")
class PageRequestsTest {

    @Test
    @DisplayName("página 1 do contrato é o índice 0 do Spring")
    void oneBasedBecomesZeroBased() {
        assertEquals(0, PageRequests.of(1, 10).getPageNumber());
        assertEquals(4, PageRequests.of(5, 10).getPageNumber());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -999})
    @DisplayName("página zero ou negativa cai na primeira, sem erro")
    void nonPositivePageFallsBackToFirst(int pagina) {
        assertEquals(0, PageRequests.of(pagina, 10).getPageNumber());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("tamanho ausente ou inválido cai no default")
    void invalidSizeFallsBackToDefault(int tamanho) {
        assertEquals(PageRequests.DEFAULT_PAGE_SIZE, PageRequests.of(1, tamanho).getPageSize());
    }

    @Test
    @DisplayName("tamanho dentro do limite é respeitado")
    void sizeWithinLimitIsKept() {
        assertEquals(50, PageRequests.of(1, 50).getPageSize());
        assertEquals(
                PageRequests.MAX_PAGE_SIZE,
                PageRequests.of(1, PageRequests.MAX_PAGE_SIZE).getPageSize());
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 1_000, 100_000, Integer.MAX_VALUE})
    @DisplayName("acima do teto é cortado, não recusado")
    void oversizedIsClampedNotRejected(int tamanho) {
        // Sem teto, ?pageSize=100000 devolvia a tabela inteira numa requisição - na rota mais
        // chamada da aplicação. Cortar em silêncio, e não recusar com 400, porque pedir demais
        // não é erro de quem chama: o bloco `pagination` da resposta devolve o tamanho usado.
        assertEquals(PageRequests.MAX_PAGE_SIZE, PageRequests.of(1, tamanho).getPageSize());
    }

    @Test
    @DisplayName("a ordenação passa intacta")
    void sortIsPreserved() {
        Sort ordem = Sort.by(Sort.Direction.DESC, "updatedAt");
        Pageable pageable = PageRequests.of(2, 25, ordem);

        assertEquals(ordem, pageable.getSort());
        assertEquals(1, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
    }
}
