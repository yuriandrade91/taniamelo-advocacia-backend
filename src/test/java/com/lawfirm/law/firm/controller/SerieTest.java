package com.lawfirm.law.firm.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("basis das séries mensais: due (competência) ou paid (caixa)")
class SerieTest {

    @Test
    @DisplayName("ausente ou due significa competência")
    void padraoEhCompetencia() {
        assertFalse(Serie.porPagamento("basis", null));
        assertFalse(Serie.porPagamento("basis", ""));
        assertFalse(Serie.porPagamento("basis", "due"));
        assertFalse(Serie.porPagamento("basis", "DUE"));
    }

    @Test
    @DisplayName("paid significa caixa")
    void paidEhCaixa() {
        assertTrue(Serie.porPagamento("basis", "paid"));
        assertTrue(Serie.porPagamento("basis", "Paid"));
    }

    /**
     * O ponto do teste: valor estranho NÃO cai no padrão. Quem escreveu {@code basis=pago} está
     * pedindo caixa; devolver competência entregaria outro número com a mesma cara.
     */
    @Test
    @DisplayName("valor desconhecido é recusado em vez de virar o padrão")
    void desconhecidoNaoViraPadrao() {
        ValidationException ex =
                assertThrows(ValidationException.class, () -> Serie.porPagamento("basis", "pago"));
        assertEquals("basis", ex.getField());
    }
}
