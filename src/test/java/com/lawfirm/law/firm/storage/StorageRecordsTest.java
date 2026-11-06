package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@DisplayName("Records do storage: StoredFile, LoadedFile e FileDownload")
class StorageRecordsTest {

    @Test
    void storedFileExposesItsComponents() {
        StoredFile stored = new StoredFile("docs/a.pdf", "application/pdf", 42L);

        assertEquals("docs/a.pdf", stored.storageKey());
        assertEquals("application/pdf", stored.mimeType());
        assertEquals(42L, stored.sizeBytes());
        assertEquals(new StoredFile("docs/a.pdf", "application/pdf", 42L), stored);
        assertNotEquals(new StoredFile("docs/b.pdf", "application/pdf", 42L), stored);
        assertTrue(stored.toString().contains("docs/a.pdf"));
    }

    @Test
    void loadedFileCarriesResourceAndMimeType() {
        Resource resource = new ByteArrayResource("x".getBytes());
        LoadedFile loaded = new LoadedFile(resource, "image/png");

        assertSame(resource, loaded.resource());
        assertEquals("image/png", loaded.mimeType());
        assertEquals(new LoadedFile(resource, "image/png"), loaded);
    }

    @Test
    void fileDownloadCarriesEverythingTheControllerNeeds() {
        Resource resource = new ByteArrayResource("x".getBytes());
        FileDownload download = new FileDownload(resource, "application/pdf", "cnis.pdf", 7L);

        assertSame(resource, download.resource());
        assertEquals("application/pdf", download.mimeType());
        assertEquals("cnis.pdf", download.originalFilename());
        assertEquals(7L, download.sizeBytes());
        assertEquals(
                download.hashCode(),
                new FileDownload(resource, "application/pdf", "cnis.pdf", 7L).hashCode());
    }
}
