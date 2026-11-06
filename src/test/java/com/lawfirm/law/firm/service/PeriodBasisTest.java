package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.exception.ValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Competência e caixa são perguntas diferentes, e misturá-las é o erro mais caro do domínio
 * financeiro: somar recebimento por vencimento e apresentar como "entrou" conta dinheiro que não
 * chegou. O erro é silencioso — os números parecem plausíveis — e só aparece na conferência com o
 * extrato.
 */
@DisplayName("Recorte de data: ou competência, ou caixa")
class PeriodBasisTest {

    private static final LocalDate DIA = LocalDate.of(2026, 5, 10);

    @Test
    @DisplayName("os dois pares na mesma chamada são recusados, nomeando o campo")
    void doisRecortesRecusados() {
        ValidationException ex =
                assertThrows(
                        ValidationException.class,
                        () -> PeriodBasis.requireSingleRange(DIA, DIA, DIA, DIA));
        assertEquals("dueFrom", ex.getField());
    }

    @Test
    @DisplayName("basta UMA ponta de cada par para haver conflito")
    void umaPontaDeCadaJaConflita() {
        assertThrows(
                ValidationException.class,
                () -> PeriodBasis.requireSingleRange(DIA, null, null, DIA));
    }

    @Test
    @DisplayName("um par só, ou nenhum, passa")
    void umParSoPassa() {
        assertDoesNotThrow(() -> PeriodBasis.requireSingleRange(DIA, DIA, null, null));
        assertDoesNotThrow(() -> PeriodBasis.requireSingleRange(null, null, DIA, DIA));
        assertDoesNotThrow(() -> PeriodBasis.requireSingleRange(null, null, null, null));
    }

    @Test
    @DisplayName("é caixa quando qualquer ponta de pagamento vier preenchida")
    void reconheceCaixa() {
        assertTrue(PeriodBasis.isCashBasis(DIA, null));
        assertTrue(PeriodBasis.isCashBasis(null, DIA));
        assertFalse(PeriodBasis.isCashBasis(null, null));
    }
}
