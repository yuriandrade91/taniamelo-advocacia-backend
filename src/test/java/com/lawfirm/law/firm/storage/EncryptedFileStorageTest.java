package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@DisplayName("EncryptedFileStorage: o storage só vê o arquivo do cliente cifrado")
class EncryptedFileStorageTest {

    private static final byte[] PDF =
            "%PDF-1.7 CNIS de Maria da Silva".getBytes(StandardCharsets.UTF_8);

    private final FileEncryption encryption = new FileEncryption(FileEncryptionKeyGuard.DEV_KEY, 1);

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("files", "cnis.pdf", "application/pdf", PDF);
    }

    @Nested
    @DisplayName("sobre o disco local")
    class OnLocalDisk {

        @TempDir Path base;

        private EncryptedFileStorage storage() {
            return new EncryptedFileStorage(
                    new LocalDiskFileStorageService(base.toString()), encryption);
        }

        @Test
        @DisplayName("o arquivo gravado no disco é o conteúdo cifrado, não o PDF")
        void diskHoldsCiphertext() throws IOException {
            StoredFile stored = storage().store(pdf(), "tenant_x/clients/1/documents");

            byte[] onDisk = Files.readAllBytes(base.resolve(stored.storageKey()));
            assertTrue(FileEncryption.isEncrypted(onDisk));
            assertFalse(new String(onDisk, StandardCharsets.ISO_8859_1).contains("Maria"));
        }

        @Test
        @DisplayName("devolve o tipo e o tamanho do original, que é o que vai para o banco")
        void returnsOriginalMetadata() {
            StoredFile stored = storage().store(pdf(), "docs");

            assertEquals("application/pdf", stored.mimeType());
            assertEquals(PDF.length, stored.sizeBytes());
            assertTrue(stored.storageKey().endsWith(".pdf"));
        }

        @Test
        @DisplayName("a leitura devolve o PDF original")
        void loadDecrypts() throws IOException {
            EncryptedFileStorage storage = storage();
            StoredFile stored = storage.store(pdf(), "docs");

            try (InputStream in = storage.load(stored.storageKey()).resource().getInputStream()) {
                assertArrayEquals(PDF, in.readAllBytes());
            }
        }

        @Test
        @DisplayName("arquivo gravado antes da criptografia é lido como está")
        void legacyPlainFileIsReadAsIs() throws IOException {
            Path legacy = base.resolve("antigo/cnis.pdf");
            Files.createDirectories(legacy.getParent());
            Files.write(legacy, PDF);

            try (InputStream in = storage().load("antigo/cnis.pdf").resource().getInputStream()) {
                assertArrayEquals(PDF, in.readAllBytes());
            }
        }

        @Test
        @DisplayName("arquivo cifrado com outra chave falha na leitura")
        void otherKeyFails() {
            String storageKey = storage().store(pdf(), "docs").storageKey();
            EncryptedFileStorage withOtherKey =
                    new EncryptedFileStorage(
                            new LocalDiskFileStorageService(base.toString()),
                            new FileEncryption("b3V0cmEtY2hhdmUtZGUtMzItYnl0ZXMtZGUtdGVzdGU=", 1));

            assertThrows(FileStorageException.class, () -> withOtherKey.load(storageKey));
        }

        @Test
        @DisplayName("excluir remove o arquivo do disco")
        void deleteRemoves() {
            EncryptedFileStorage storage = storage();
            StoredFile stored = storage.store(pdf(), "docs");

            storage.delete(stored.storageKey());

            assertFalse(Files.exists(base.resolve(stored.storageKey())));
        }
    }

    @Nested
    @DisplayName("sobre o S3")
    class OnObjectStorage {

        private final S3Client s3 = mock(S3Client.class);
        private final EncryptedFileStorage storage =
                new EncryptedFileStorage(
                        new ObjectStorageFileStorageService(s3, "bucket"), encryption);

        @Test
        @DisplayName("o objeto enviado é o conteúdo cifrado, com tipo genérico e o tamanho cifrado")
        void putsCiphertext() throws IOException {
            storage.store(pdf(), "docs");

            ArgumentCaptor<PutObjectRequest> request =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
            verify(s3).putObject(request.capture(), body.capture());

            byte[] sent;
            try (InputStream in = body.getValue().contentStreamProvider().newStream()) {
                sent = in.readAllBytes();
            }
            assertTrue(FileEncryption.isEncrypted(sent));
            assertEquals("application/octet-stream", request.getValue().contentType());
            assertEquals(sent.length, request.getValue().contentLength());
        }

        @Test
        @DisplayName("a leitura decifra o objeto do bucket")
        void getDecrypts() throws IOException {
            byte[] stored = encryption.encrypt(PDF);
            when(s3.getObject(any(GetObjectRequest.class)))
                    .thenReturn(
                            new ResponseInputStream<>(
                                    GetObjectResponse.builder()
                                            .contentType("application/octet-stream")
                                            .build(),
                                    new ByteArrayResource(stored).getInputStream()));

            try (InputStream in = storage.load("docs/a.pdf").resource().getInputStream()) {
                assertArrayEquals(PDF, in.readAllBytes());
            }
        }
    }

    @Test
    @DisplayName("arquivo vazio é recusado antes de cifrar")
    void emptyFileRefused() {
        EncryptedFileStorage storage =
                new EncryptedFileStorage(mock(FileStorageService.class), encryption);
        MockMultipartFile empty =
                new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[0]);

        assertThrows(FileStorageException.class, () -> storage.store(empty, "docs"));
    }
}
