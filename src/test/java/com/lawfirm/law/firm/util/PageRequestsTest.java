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
    @DisplayName("a ordenação pedida passa na frente, com o id desempatando no fim")
    void sortIsPreservedWithIdTiebreaker() {
        Pageable pageable = PageRequests.of(2, 25, Sort.by(Sort.Direction.DESC, "updatedAt"));

        // Sem o último critério, dois clientes do mesmo `updatedAt` saem na ordem que o banco
        // escolher, e ela pode mudar entre a consulta da página 1 e a da página 2: a mesma linha
        // aparece duas vezes e outra nunca aparece. O defeito foi encontrado assim, pelo teste de
        // contrato "páginas não repetem registro".
        assertEquals(
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")), pageable.getSort());
        assertEquals(1, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
    }

    @Test
    @DisplayName("quem já ordena por id não ganha um segundo critério igual")
    void explicitIdIsNotDuplicated() {
        Sort ordem = Sort.by(Sort.Order.desc("id"));
        assertEquals(ordem, PageRequests.of(1, 10, ordem).getSort());
    }

    @Test
    @DisplayName("ordenação vazia recebe só o desempate")
    void unsortedGetsOnlyTheTiebreaker() {
        assertEquals(
                Sort.by(Sort.Order.asc("id")), PageRequests.of(1, 10, Sort.unsorted()).getSort());
    }
}
