package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("LocalDiskFileStorageService: falhas de infraestrutura viram FileStorageException")
class LocalDiskFileStorageEdgeCasesTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("diretório base que não pode ser criado derruba a subida com mensagem clara")
    void unusableBaseDirectoryFailsFast() throws IOException {
        Path blocker = tempDir.resolve("arquivo-no-lugar-da-pasta");
        Files.writeString(blocker, "sou um arquivo, não uma pasta");

        FileStorageException ex =
                assertThrows(
                        FileStorageException.class,
                        () ->
                                new LocalDiskFileStorageService(
                                        blocker.resolve("dentro").toString()));
        assertTrue(ex.getMessage().contains("diretório de storage"));
    }

    @Test
    @DisplayName("falha de I/O ao gravar vira FileStorageException com a chave na mensagem")
    void writeFailureBecomesFileStorageException() throws IOException {
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(tempDir.toString());

        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("cnis.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(10L);
        doThrow(new IOException("disco cheio")).when(file).transferTo(any(Path.class));

        FileStorageException ex =
                assertThrows(FileStorageException.class, () -> storage.store(file, "docs"));
        assertTrue(ex.getMessage().contains("Falha ao gravar arquivo"));
        assertTrue(ex.getMessage().contains("docs/"));
    }

    @Test
    @DisplayName("falha de I/O ao remover vira FileStorageException")
    void deleteFailureBecomesFileStorageException() throws IOException {
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(tempDir.toString());

        // Um diretório não vazio não pode ser removido com deleteIfExists: o IOException
        // resultante deve virar FileStorageException em vez de vazar como erro cru.
        Path directory = tempDir.resolve("pasta-cheia");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("dentro.txt"), "x");

        FileStorageException ex =
                assertThrows(FileStorageException.class, () -> storage.delete("pasta-cheia"));
        assertTrue(ex.getMessage().contains("Falha ao remover arquivo"));
    }

    @Test
    @DisplayName("o mime type na leitura é inferido do conteúdo gravado")
    void loadProbesTheMimeType() throws IOException {
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(tempDir.toString());
        Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(tempDir.resolve("docs/nota.txt"), "conteudo");

        LoadedFile loaded = storage.load("docs/nota.txt");

        assertTrue(loaded.resource().exists());
        assertEquals("text/plain", loaded.mimeType());
    }

    @Test
    @DisplayName("arquivo sem extensão reconhecível cai em application/octet-stream")
    void unknownExtensionFallsBackToOctetStream() throws IOException {
        LocalDiskFileStorageService storage = new LocalDiskFileStorageService(tempDir.toString());
        Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(tempDir.resolve("docs/dado.qzx"), "conteudo");

        assertEquals("application/octet-stream", storage.load("docs/dado.qzx").mimeType());
    }
}
