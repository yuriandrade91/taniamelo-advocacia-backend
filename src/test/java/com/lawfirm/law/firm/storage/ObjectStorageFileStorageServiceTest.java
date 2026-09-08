package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ObjectStorageFileStorageService: mesmo contrato do disco, via protocolo S3")
class ObjectStorageFileStorageServiceTest {

    private static final String BUCKET = "taniamelo-arquivos";

    @Mock private S3Client s3Client;

    private ObjectStorageFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new ObjectStorageFileStorageService(s3Client, BUCKET);
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "conteudo".getBytes());
    }

    @Test
    @DisplayName("store envia o objeto com bucket, key, content-type e tamanho")
    void storeSendsPutObject() {
        StoredFile stored = storage.store(pdf("cnis.pdf"), "clients/42/simulations");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client)
                .putObject(
                        request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));

        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals(stored.storageKey(), request.getValue().key());
        assertEquals("application/pdf", request.getValue().contentType());
        assertEquals(8L, request.getValue().contentLength());
        assertTrue(stored.storageKey().startsWith("clients/42/simulations/"));
        assertTrue(stored.storageKey().endsWith(".pdf"));
    }

    @Test
    @DisplayName("a chave gerada é única por upload")
    void generatesUniqueKeys() {
        assertFalse(
                storage.store(pdf("a.pdf"), "docs")
                        .storageKey()
                        .equals(storage.store(pdf("a.pdf"), "docs").storageKey()));
    }

    @Test
    @DisplayName("diretório vazio/nulo cai em misc e separadores são normalizados")
    void normalizesDirectory() {
        assertTrue(storage.store(pdf("a.pdf"), null).storageKey().startsWith("misc/"));
        assertTrue(storage.store(pdf("a.pdf"), "  ").storageKey().startsWith("misc/"));
        assertTrue(storage.store(pdf("a.pdf"), "/docs/").storageKey().startsWith("docs/"));
        assertTrue(storage.store(pdf("a.pdf"), "a\\b").storageKey().startsWith("a/b/"));
    }

    @Test
    @DisplayName("extensão é derivada do nome original, em minúsculas")
    void derivesLowercaseExtension() {
        assertTrue(storage.store(pdf("DOC.PDF"), "docs").storageKey().endsWith(".pdf"));
        assertFalse(storage.store(pdf("sem-extensao"), "docs").storageKey().contains("."));
        assertFalse(storage.store(pdf("termina."), "docs").storageKey().endsWith("."));
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
    @DisplayName("arquivo nulo ou vazio é recusado antes de chamar o storage")
    void rejectsNullOrEmptyFile() {
        assertThrows(FileStorageException.class, () -> storage.store(null, "docs"));
        assertThrows(
                FileStorageException.class,
                () ->
                        storage.store(
                                new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[0]),
                                "docs"));
    }

    @Test
    @DisplayName("falha de I/O ao ler o multipart vira FileStorageException")
    void ioFailureBecomesFileStorageException() throws IOException {
        MultipartFile broken = org.mockito.Mockito.mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("a.pdf");
        when(broken.getContentType()).thenReturn("application/pdf");
        when(broken.getSize()).thenReturn(10L);
        when(broken.getInputStream()).thenThrow(new IOException("stream quebrado"));

        assertThrows(FileStorageException.class, () -> storage.store(broken, "docs"));
    }

    @Test
    @DisplayName("erro do provedor no upload vira FileStorageException")
    void providerErrorOnPutBecomesFileStorageException() {
        when(s3Client.putObject(
                        any(PutObjectRequest.class),
                        any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(S3Exception.builder().message("acesso negado").build());

        assertThrows(FileStorageException.class, () -> storage.store(pdf("a.pdf"), "docs"));
    }

    private ResponseInputStream<GetObjectResponse> responseWith(String contentType) {
        InputStream body = new ByteArrayInputStream("conteudo".getBytes());
        GetObjectResponse response =
                contentType == null
                        ? GetObjectResponse.builder().build()
                        : GetObjectResponse.builder().contentType(contentType).build();
        return new ResponseInputStream<>(response, AbortableInputStream.create(body));
    }

    @Test
    @DisplayName("load devolve o recurso e o mime type informado pelo provedor")
    void loadReturnsResourceAndMimeType() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseWith("application/pdf"));

        LoadedFile loaded = storage.load("docs/a.pdf");

        assertEquals("application/pdf", loaded.mimeType());
        assertTrue(loaded.resource().exists());

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("docs/a.pdf", request.getValue().key());
    }

    @Test
    @DisplayName("sem content-type no objeto, cai no octet-stream")
    void loadWithoutContentTypeFallsBack() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseWith(null));
        assertEquals("application/octet-stream", storage.load("docs/a.pdf").mimeType());
    }

    @Test
    @DisplayName("chave inexistente vira FileStorageException com a chave na mensagem")
    void loadOfMissingKeyThrows() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("não existe").build());

        FileStorageException ex =
                assertThrows(FileStorageException.class, () -> storage.load("docs/sumiu.pdf"));
        assertTrue(ex.getMessage().contains("docs/sumiu.pdf"));
    }

    @Test
    @DisplayName("erro genérico do provedor na leitura vira FileStorageException")
    void loadProviderErrorThrows() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("indisponível").build());

        assertThrows(FileStorageException.class, () -> storage.load("docs/a.pdf"));
    }

    @Test
    @DisplayName("delete envia deleteObject com bucket e key")
    void deleteSendsDeleteObject() {
        storage.delete("docs/a.pdf");

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("docs/a.pdf", request.getValue().key());
    }

    @Test
    @DisplayName("erro do provedor no delete vira FileStorageException")
    void deleteProviderErrorThrows() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("acesso negado").build());

        assertThrows(FileStorageException.class, () -> storage.delete("docs/a.pdf"));
    }

    @Test
    @DisplayName("FileStorageException preserva a causa original para o log")
    void fileStorageExceptionKeepsCause() {
        RuntimeException cause = new RuntimeException("raiz");
        FileStorageException ex = new FileStorageException("falhou", cause);
        assertSame(cause, ex.getCause());
        assertEquals("falhou", ex.getMessage());
        assertEquals("só mensagem", new FileStorageException("só mensagem").getMessage());
    }
}
