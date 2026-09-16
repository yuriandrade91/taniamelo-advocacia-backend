package com.lawfirm.law.firm.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolução padrão "label PT-BR ou nome do enum" usada por todos os enums de domínio da aplicação.
 * Aceita o nome da constante (ex.: APOSENTADORIA_RURAL), o label humano (ex.: "Aposentadoria
 * rural") ou qualquer variação sem acento/pontuação, sempre case-insensitive.
 */
public final class EnumLabelSupport {

    private EnumLabelSupport() {}

    public static <E extends Enum<E>> E fromLabel(
            Class<E> type, Function<E, String> labelFn, String raw) {
        return fromLabel(type, labelFn, raw, Map.of());
    }

    /**
     * Mesma resolução, mais um mapa de <b>apelidos</b>: escritas que não são mais valores do enum
     * mas continuam aceitas na entrada, resolvendo para o valor canônico. É o que permite aposentar
     * um nome sem quebrar payload antigo nem dado já gravado.
     *
     * <p>Os apelidos são consultados <b>depois</b> dos valores reais: um valor do enum nunca é
     * encoberto por um apelido, aconteça o que acontecer com o mapa.
     *
     * @param apelidos chave já passada por {@link #normalize}
     */
    public static <E extends Enum<E>> E fromLabel(
            Class<E> type, Function<E, String> labelFn, String raw, Map<String, E> apelidos) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) return null;

        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(cleaned) || labelFn.apply(e).equalsIgnoreCase(cleaned))
                return e;
        }
        String normalized = normalize(cleaned);
        for (E e : type.getEnumConstants()) {
            if (normalize(e.name()).equals(normalized)
                    || normalize(labelFn.apply(e)).equals(normalized)) return e;
        }
        E apelido = apelidos.get(normalized);
        if (apelido != null) return apelido;

        String validValues =
                Arrays.stream(type.getEnumConstants())
                        .map(labelFn)
                        .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Valor inválido: \"" + raw + "\". Valores aceitos: " + validValues);
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^\\p{Alnum}]+", "").toLowerCase();
    }
}
