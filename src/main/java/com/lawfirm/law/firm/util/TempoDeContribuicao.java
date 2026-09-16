package com.lawfirm.law.firm.util;

/**
 * Tempo de contribuição em anos, meses e dias.
 *
 * <p>Substitui o {@code ContributionTimeParser}, que recebia texto livre ("3 anos, 10 meses, 22
 * dias") e tentava adivinhar os números com expressão regular. Adivinhar tinha dois defeitos reais:
 * "nao informado" virava {@code 0} - indistinguível de quem de fato não contribuiu - e "300000000
 * anos" estourava o {@code int} e gravava {@code -694967296}. Nenhum dos dois era recusado; os dois
 * viravam dado.
 *
 * <p>A tela agora manda três números separados, que é como o cálculo previdenciário é escrito e
 * como o INSS apresenta o extrato. Cada campo tem faixa própria validada no DTO, então não há texto
 * para interpretar nem estouro possível.
 *
 * <p>Convenção do domínio: mês de 30 dias, ano de 12 meses. Por isso meses vai até 11 e dias até 29
 * - 12 meses são 1 ano e 30 dias são 1 mês, e aceitar as duas escritas deixaria a mesma duração
 * gravada de dois jeitos diferentes.
 */
public final class TempoDeContribuicao {

    public static final int MAX_ANOS = 130;
    public static final int MAX_MESES = 11;
    public static final int MAX_DIAS = 29;

    private TempoDeContribuicao() {}

    /**
     * Total em meses, que é a unidade com que as regras de carência e de transição trabalham.
     *
     * <p>Dia a partir de 15 arredonda para cima um mês - mesma regra que o parser antigo aplicava,
     * mantida para não mudar o número de quem já está cadastrado.
     *
     * <p>Devolve {@code null} quando os três vêm nulos: ninguém informou o tempo, que é diferente
     * de informar zero.
     */
    public static Integer emMeses(Integer anos, Integer meses, Integer dias) {
        if (anos == null && meses == null && dias == null) {
            return null;
        }
        int a = anos == null ? 0 : anos;
        int m = meses == null ? 0 : meses;
        int d = dias == null ? 0 : dias;
        return (a * 12) + m + (d >= 15 ? 1 : 0);
    }

    /**
     * Forma de exibição ("33 anos, 11 meses e 5 dias"), derivada na resposta para a tela não ter de
     * montar a frase. Não é gravada: o que está no banco são os três números.
     *
     * <p>Parcela zerada não aparece, porque "3 anos" se lê melhor que "3 anos, 0 meses e 0 dias" -
     * a não ser que as três sejam zero, aí o zero é a informação.
     */
    public static String formatar(Integer anos, Integer meses, Integer dias) {
        if (anos == null && meses == null && dias == null) {
            return null;
        }
        int a = anos == null ? 0 : anos;
        int m = meses == null ? 0 : meses;
        int d = dias == null ? 0 : dias;

        java.util.List<String> partes = new java.util.ArrayList<>(3);
        if (a > 0) {
            partes.add(a + (a == 1 ? " ano" : " anos"));
        }
        if (m > 0) {
            partes.add(m + (m == 1 ? " mês" : " meses"));
        }
        if (d > 0) {
            partes.add(d + (d == 1 ? " dia" : " dias"));
        }
        if (partes.isEmpty()) {
            return "0 dias";
        }
        if (partes.size() == 1) {
            return partes.get(0);
        }
        String ultima = partes.remove(partes.size() - 1);
        return String.join(", ", partes) + " e " + ultima;
    }
}
