package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import java.time.LocalDate;

/**
 * Competência ou caixa — uma pergunta por chamada.
 *
 * <p>{@code dueFrom}/{@code dueTo} recortam por vencimento; {@code paidFrom}/{@code paidTo}, por
 * data de pagamento. Receber os dois pares na mesma requisição não tem resposta certa, e escolher
 * um por conta própria é o pior dos caminhos: quem perguntou "quanto entrou em maio" receberia
 * "quanto vence em maio", com números plausíveis e uma diferença que só aparece na conferência com
 * o extrato.
 */
public final class RecorteDeData {

    private RecorteDeData() {}

    public static void exigirUmRecorte(
            LocalDate dueFrom, LocalDate dueTo, LocalDate paidFrom, LocalDate paidTo) {
        boolean porVencimento = dueFrom != null || dueTo != null;
        boolean porPagamento = paidFrom != null || paidTo != null;
        if (porVencimento && porPagamento) {
            throw new ValidationException(
                    "dueFrom",
                    ValidationErrorCode.INVALID_DATE_RANGE,
                    "Escolha um recorte: vencimento (dueFrom/dueTo) ou pagamento"
                            + " (paidFrom/paidTo). Somar um como se fosse o outro conta dinheiro"
                            + " que não chegou.");
        }
    }

    /** {@code true} quando a pergunta é de caixa (recorte por data de pagamento). */
    public static boolean ehCaixa(LocalDate paidFrom, LocalDate paidTo) {
        return paidFrom != null || paidTo != null;
    }
}
