package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Converte texto de query string em enum, falhando alto.
 *
 * <p>O caminho fácil é ignorar o valor que não casa. O resultado é um filtro silenciosamente
 * desligado: quem pediu {@code status=Pendnte} recebe a lista inteira, conclui que não há nada
 * errado com o filtro e toma a decisão olhando o número de outra pergunta. Já aconteceu em {@code
 * /clients}.
 */
public final class EnumsDeRequisicao {

    private EnumsDeRequisicao() {}

    public static <E extends Enum<E>> List<E> lista(
            String campo, List<String> brutos, Function<String, E> deRotulo) {
        if (brutos == null || brutos.isEmpty()) return null;
        List<E> resultado = new ArrayList<>();
        for (String bruto : brutos) {
            resultado.add(unico(campo, bruto, deRotulo));
        }
        return resultado;
    }

    public static <E extends Enum<E>> E unico(
            String campo, String bruto, Function<String, E> deRotulo) {
        if (bruto == null || bruto.isBlank()) return null;
        try {
            return deRotulo.apply(bruto);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(
                    campo, ValidationErrorCode.INVALID_ENUM_VALUE, ex.getMessage());
        }
    }
}
