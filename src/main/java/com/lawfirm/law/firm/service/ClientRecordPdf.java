package com.lawfirm.law.firm.service;

import java.util.Arrays;
import java.util.Objects;

/** O PDF pronto da ficha e o nome sugerido para o arquivo baixado. */
public record ClientRecordPdf(String filename, byte[] content) {

    /*
     * O record compara arrays por referência: dois PDFs idênticos seriam "diferentes". Estes três
     * métodos olham o conteúdo; o toString mostra só o tamanho, porque despejar o PDF no log não
     * ajuda ninguém.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ClientRecordPdf(String otherFilename, byte[] otherContent)
                && Objects.equals(filename, otherFilename)
                && Arrays.equals(content, otherContent);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(filename) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "ClientRecordPdf[filename="
                + filename
                + ", content="
                + (content == null ? 0 : content.length)
                + " bytes]";
    }
}
