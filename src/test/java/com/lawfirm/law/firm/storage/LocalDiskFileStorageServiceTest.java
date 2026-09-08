package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("LocalDiskFileStorageService: gravação em disco com chave opaca")
class LocalDiskFileStorageServiceTest {

    @TempDir Path tempDir;

    private LocalDiskFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskFileStorageService(tempDir.toString());
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile(
                "files", name, "application/pdf", "conteudo".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("o diretório base é criado na subida se não existir")
    void createsBaseDirectoryOnStartup() {
        Path nested = tempDir.resolve("a/b/c");
        new LocalDiskFileStorageService(nested.toString());
        assertTrue(Files.isDirectory(nested));
    }

    @Test
    @DisplayName("store grava o arquivo e devolve chave relativa, mime e tamanho")
    void storeWritesFileAndReturnsMetadata() {
        MockMultipartFile file = pdf("cnis.pdf");

        StoredFile stored = storage.store(file, "clients/42/documents");

        assertTrue(stored.storageKey().startsWith("clients/42/documents/"));
        assertTrue(stored.storageKey().endsWith(".pdf"));
        assertEquals("application/pdf", stored.mimeType());
        assertEquals(file.getSize(), stored.sizeBytes());
        assertTrue(Files.exists(tempDir.resolve(stored.storageKey())));
        assertFalse(
                stored.storageKey().contains(tempDir.toString()), "nunca expõe caminho absoluto");
    }

    @Test
    @DisplayName("o nome gravado é um UUID - dois uploads do mesmo arquivo não colidem")
    void generatesUniqueNamesPerUpload() {
        String first = storage.store(pdf("igual.pdf"), "docs").storageKey();
        String second = storage.store(pdf("igual.pdf"), "docs").storageKey();

        assertFalse(first.equals(second));
        assertTrue(Files.exists(tempDir.resolve(first)));
        assertTrue(Files.exists(tempDir.resolve(second)));
    }

    @Test
    @DisplayName("o nome original nunca vira o nome no disco (evita colisão e path traversal)")
    void originalFilenameIsNotUsedOnDisk() {
        StoredFile stored = storage.store(pdf("../../etc/passwd.pdf"), "docs");
        assertFalse(stored.storageKey().contains(".."));
        assertTrue(Files.exists(tempDir.resolve(stored.storageKey())));
    }

    @ParameterizedTest(name = "\"{0}\" -> chave termina em \"{1}\"")
    @CsvSource({
        "documento.PDF, .pdf",
        "foto.JPEG, .jpeg",
        "arquivo.tar.gz, .gz",
        "'', ''",
        "sem-extensao, ''",
        "termina-com-ponto., ''"
    })
    void derivesExtensionFromOriginalName(String originalName, String expectedSuffix) {
        StoredFile stored =
                storage.store(
                        new MockMultipartFile(
                                "files", originalName, "application/pdf", "x".getBytes()),
                        "docs");
        assertTrue(
                stored.storageKey().endsWith(expectedSuffix),
                stored.storageKey() + " deveria terminar com '" + expectedSuffix + "'");
    }

    @Test
    @DisplayName("nome original nulo não quebra o upload")
    void nullOriginalFilenameIsHandled() {
        StoredFile stored =
                storage.store(
                        new MockMultipartFile("files", null, "application/pdf", "x".getBytes()),
                        "docs");
        assertTrue(Files.exists(tempDir.resolve(stored.storageKey())));
    }

    @ParameterizedTest(name = "diretório \"{0}\" é normalizado para \"{1}\"")
    @CsvSource({
        "clients/1/docs, clients/1/docs",
        "/clients/1/docs/, clients/1/docs",
        "//clients//1//, clients//1",
        "'', misc",
        "'   ', misc"
    })
    void normalizesDirectory(String directory, String expectedPrefix) {
        StoredFile stored = storage.store(pdf("a.pdf"), directory);
        assertTrue(
                stored.storageKey().startsWith(expectedPrefix + "/"),
                stored.storageKey() + " deveria começar com " + expectedPrefix);
    }

    @Test
    @DisplayName("diretório nulo cai em misc")
    void nullDirectoryFallsBackToMisc() {
        assertTrue(storage.store(pdf("a.pdf"), null).storageKey().startsWith("misc/"));
    }

    @Test
    @DisplayName("separadores do Windows são convertidos")
    void convertsWindowsSeparators() {
        assertTrue(
                storage.store(pdf("a.pdf"), "clients\\1\\docs")
                        .storageKey()
                        .startsWith("clients/1/docs/"));
    }

    @Test
    @DisplayName("content-type ausente vira application/octet-stream")
    void missingContentTypeFallsBack() {
        StoredFile stored =
                storage.store(
                        new MockMultipartFile("files", "a.bin", null, "x".getBytes()), "docs");
        assertEquals("application/octet-stream", stored.mimeType());
    }

    @Test
    @DisplayName("arquivo nulo ou vazio é recusado")
    void rejectsNullOrEmptyFile() {
        assertThrows(FileStorageException.class, () -> storage.store(null, "docs"));
        assertThrows(
                FileStorageException.class,
                () ->
                        storage.store(
                                new MockMultipartFile(
                                        "files", "vazio.pdf", "application/pdf", new byte[0]),
                                "docs"));
    }

    @Test
    @DisplayName("load devolve o conteúdo gravado")
    void loadReturnsStoredContent() throws IOException {
        StoredFile stored = storage.store(pdf("cnis.pdf"), "docs");

        LoadedFile loaded = storage.load(stored.storageKey());

        assertTrue(loaded.resource().exists());
        assertArrayEquals(
                "conteudo".getBytes(StandardCharsets.UTF_8),
                loaded.resource().getInputStream().readAllBytes());
    }

    @Test
    @DisplayName("load de chave inexistente estoura FileStorageException")
    void loadOfMissingKeyThrows() {
        FileStorageException ex =
                assertThrows(FileStorageException.class, () -> storage.load("docs/nao-existe.pdf"));
        assertTrue(ex.getMessage().contains("nao-existe.pdf"));
    }

    @Test
    @DisplayName("delete remove o arquivo e é idempotente")
    void deleteIsIdempotent() {
        StoredFile stored = storage.store(pdf("a.pdf"), "docs");

        storage.delete(stored.storageKey());
        assertFalse(Files.exists(tempDir.resolve(stored.storageKey())));

        storage.delete(stored.storageKey());
        storage.delete("docs/nunca-existiu.pdf");
    }

    @Test
    @DisplayName("path traversal na chave é recusado em load, delete e store")
    void rejectsPathTraversalInStorageKey() {
        for (String key :
                new String[] {"../fora.pdf", "docs/../../fora.pdf", "../../../etc/passwd"}) {
            FileStorageException ex =
                    assertThrows(FileStorageException.class, () -> storage.load(key), key);
            assertTrue(ex.getMessage().contains("Chave de storage inválida"));
            assertThrows(FileStorageException.class, () -> storage.delete(key), key);
        }
    }
}
