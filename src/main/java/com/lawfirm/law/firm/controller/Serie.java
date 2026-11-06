package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;

/**
 * Leitura do parâmetro {@code basis} das séries mensais.
 *
 * <p>Vale para as três rotas de timeline, e a regra é a mesma: {@code due} pergunta por
 * competência, {@code paid} por caixa. Um valor fora desses dois é recusado em vez de cair no
 * padrão — quem digitou {@code basis=pago} está pedindo caixa e receberia competência, com números
 * plausíveis e a conclusão errada.
 */
final class Serie {

    private Serie() {}

    static boolean porPagamento(String campo, String basis) {
        if (basis == null || basis.isBlank() || "due".equalsIgnoreCase(basis)) return false;
        if ("paid".equalsIgnoreCase(basis)) return true;
        throw new ValidationException(
                campo,
                ValidationErrorCode.INVALID_ENUM_VALUE,
                "Valor inválido para basis: '"
                        + basis
                        + "'. Use 'due' (competência) ou 'paid' (caixa).");
    }
}
