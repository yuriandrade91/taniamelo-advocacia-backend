package com.lawfirm.law.firm.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Storage dos arquivos de cliente: cifra na gravação e decifra na leitura, por cima do {@link
 * FileStorageService} configurado (disco ou S3).
 *
 * <p>Fica por fora da interface, e não como mais uma implementação dela, para que disco e S3 não
 * precisem saber de criptografia e para que quem não guarda dado de cliente (a logo do escritório,
 * que é pública) continue usando o storage direto.
 *
 * <p>Arquivos gravados antes da criptografia não têm o cabeçalho de {@link FileEncryption} e são
 * devolvidos como estão. Eram massa de teste; tudo o que é gravado por aqui já sai cifrado.
 *
 * <p>O arquivo inteiro passa pela memória, na ida e na volta. O limite de upload (10 MB por
 * arquivo, {@code spring.servlet.multipart}) é o que torna isso aceitável; um limite bem maior
 * pediria cifra em fluxo.
 */
@Component
public class EncryptedFileStorage {

    private static final String DEFAULT_MIME = "application/octet-stream";

    private final FileStorageService storage;
    private final FileEncryption encryption;

    public EncryptedFileStorage(FileStorageService storage, FileEncryption encryption) {
        this.storage = storage;
        this.encryption = encryption;
    }

    /**
     * Cifra e grava. O tipo e o tamanho devolvidos são os do arquivo original, que é o que vai para
     * o banco: o download devolve o original, e o {@code Content-Length} sai desse tamanho.
     */
    public StoredFile store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Arquivo vazio ou ausente");
        }
        byte[] encrypted = encryption.encrypt(bytesOf(file));
        StoredFile stored =
                storage.store(
                        new EncryptedUpload(file.getOriginalFilename(), encrypted), directory);
        String mimeType = file.getContentType() != null ? file.getContentType() : DEFAULT_MIME;
        return new StoredFile(stored.storageKey(), mimeType, file.getSize());
    }

    public LoadedFile load(String storageKey) {
        LoadedFile loaded = storage.load(storageKey);
        byte[] stored = bytesOf(loaded.resource());
        byte[] content = FileEncryption.isEncrypted(stored) ? encryption.decrypt(stored) : stored;
        return new LoadedFile(new ByteArrayResource(content), loaded.mimeType());
    }

    public void delete(String storageKey) {
        storage.delete(storageKey);
    }

    /** Move o objeto já cifrado; o conteúdo não é tocado, então o cabeçalho continua válido. */
    public void move(String fromKey, String toKey) {
        storage.move(fromKey, toKey);
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FileStorageException("Falha ao ler o arquivo enviado", e);
        }
    }

    private static byte[] bytesOf(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new FileStorageException("Falha ao ler arquivo do storage", e);
        }
    }

    /**
     * O conteúdo já cifrado, no formato que o {@link FileStorageService} recebe. Mantém o nome
     * original só para a chave gerada preservar a extensão, como sempre; o tipo declarado é o
     * genérico, porque o objeto gravado não é mais um PDF ou uma imagem.
     */
    private static final class EncryptedUpload implements MultipartFile {

        private final String originalFilename;
        private final byte[] content;

        private EncryptedUpload(String originalFilename, byte[] content) {
            this.originalFilename = originalFilename;
            this.content = content;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return DEFAULT_MIME;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) {
            try {
                Files.write(dest.toPath(), content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
