package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClientRecordPdf: igualdade pelo conteúdo do PDF")
class ClientRecordPdfTest {

    @Test
    @DisplayName("dois PDFs com o mesmo nome e os mesmos bytes são iguais")
    void comparesTheContentAndNotTheArrayReference() {
        ClientRecordPdf first = new ClientRecordPdf("Ficha.pdf", new byte[] {1, 2, 3});
        ClientRecordPdf second = new ClientRecordPdf("Ficha.pdf", new byte[] {1, 2, 3});

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    @DisplayName("nome ou bytes diferentes, ou outro tipo, não são iguais")
    void differsWhenAnythingDiffers() {
        ClientRecordPdf pdf = new ClientRecordPdf("Ficha.pdf", new byte[] {1, 2, 3});

        assertNotEquals(new ClientRecordPdf("Outra.pdf", new byte[] {1, 2, 3}), pdf);
        assertNotEquals(new ClientRecordPdf("Ficha.pdf", new byte[] {1, 2, 4}), pdf);
        // Outro tipo e null: o assertNotEquals do JUnit não aceita tipos diferentes, e é o próprio
        // equals que se quer exercitar.
        assertFalse(isEqual(pdf, "Ficha.pdf"));
        assertFalse(isEqual(pdf, null));
    }

    private static boolean isEqual(Object left, Object right) {
        return left.equals(right);
    }

    @Test
    @DisplayName("o toString mostra o tamanho e não despeja o PDF")
    void toStringShowsTheSizeOnly() {
        String text = new ClientRecordPdf("Ficha.pdf", new byte[1024]).toString();

        assertTrue(text.contains("Ficha.pdf"), text);
        assertTrue(text.contains("1024 bytes"), text);
        assertFalse(text.contains("[B@"), text);
        assertTrue(new ClientRecordPdf("x", null).toString().contains("0 bytes"));
    }
}
