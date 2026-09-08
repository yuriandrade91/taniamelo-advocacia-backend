package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AllowedMimeTypes: whitelist de upload")
class AllowedMimeTypesTest {

    @Test
    @DisplayName("documentos aceitam PDF, PNG e JPEG")
    void documentsAcceptPdfAndImages() {
        assertEquals(3, AllowedMimeTypes.DOCUMENTS.size());
        assertTrue(AllowedMimeTypes.DOCUMENTS.contains("application/pdf"));
        assertTrue(AllowedMimeTypes.DOCUMENTS.contains("image/png"));
        assertTrue(AllowedMimeTypes.DOCUMENTS.contains("image/jpeg"));
    }

    @Test
    @DisplayName("simulações só aceitam PDF (export do Meu INSS)")
    void simulationsAcceptOnlyPdf() {
        assertEquals(1, AllowedMimeTypes.SIMULATIONS.size());
        assertTrue(AllowedMimeTypes.SIMULATIONS.contains("application/pdf"));
    }

    @Test
    @DisplayName("tipos executáveis e de escritório não entram na whitelist")
    void rejectsDangerousAndUnexpectedTypes() {
        for (String mime :
                new String[] {
                    "application/x-msdownload",
                    "text/html",
                    "application/zip",
                    "image/svg+xml",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                }) {
            assertFalse(AllowedMimeTypes.DOCUMENTS.contains(mime), mime);
            assertFalse(AllowedMimeTypes.SIMULATIONS.contains(mime), mime);
        }
        assertFalse(AllowedMimeTypes.SIMULATIONS.contains("image/png"));
    }
}
