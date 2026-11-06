package com.lawfirm.law.firm.util;

/**
 * Normalização dos documentos que IDENTIFICAM o cliente.
 *
 * <p>Existe por causa de um defeito real: dava para cadastrar a mesma pessoa duas vezes, uma com
 * "39053344705" e outra com "390.533.447-05". O validador de CPF já tirava a pontuação antes de
 * validar, e a busca já comparava por dígitos - só a checagem de duplicidade e o índice único
 * comparavam o texto cru. O sistema sabia que os dois formatos existem na leitura e ignorava isso
 * na escrita.
 *
 * <p>A decisão é guardar normalizado, e não guardar como veio e comparar normalizado: um índice
 * sobre expressão barraria o cadastro duplicado mas deixaria o banco com os dois formatos
 * convivendo, que é a bagunça que se está limpando. Exportação e relatório mostrariam formatos
 * misturados.
 *
 * <p>Formatação é responsabilidade da tela (o frontend já mascara CPF).
 */
public final class DocumentoIdentidade {

    private DocumentoIdentidade() {}

    /**
     * CPF, NIT/PIS, CTPS e número do benefício: documentos numéricos. Pontuação é formatação, não
     * informação.
     */
    public static String apenasDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("\\D", "");
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * RG: alfanumérico, porque o formato varia por estado e o órgão emissor pode aparecer no número
     * ("MG-12.345.678"). Tira pontuação e espaço, sobe para maiúscula - "mg 12.345.678" e
     * "MG12345678" são o mesmo documento.
     */
    public static String alfanumericoMaiusculo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.replaceAll("[^\\p{Alnum}]", "").toUpperCase();
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * Campo de texto comum: só apara. Devolve null para vazio, porque "" e null significam a mesma
     * coisa no banco e manter os dois faz o índice único tratá-los como valores diferentes.
     */
    public static String aparado(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
