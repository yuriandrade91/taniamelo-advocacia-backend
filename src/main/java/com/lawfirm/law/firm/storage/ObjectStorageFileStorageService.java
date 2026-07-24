package com.lawfirm.law.firm.storage;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Implementação em armazenamento de objetos (protocolo S3) do {@link FileStorageService}, ativada
 * por {@code app.storage.type=s3}. O nome é "ObjectStorage" e não "S3" porque o cliente aponta para
 * qualquer endpoint compatível com a API S3 - AWS S3 real (padrão), Cloudflare R2, MinIO ou
 * LocalStack (ver {@code app.storage.s3.endpoint}) - sem prender o código a um provedor específico.
 * Espelha exatamente o contrato do {@link LocalDiskFileStorageService}: o {@code storageKey}
 * devolvido é a chave (key) do objeto no bucket - opaca para o resto do sistema, é isso que fica
 * gravado no banco. Nenhuma entidade/DTO/service/controller muda ao trocar de disco local para
 * armazenamento de objetos; só a propriedade de configuração.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class ObjectStorageFileStorageService implements FileStorageService {

    private static final Logger log =
            LoggerFactory.getLogger(ObjectStorageFileStorageService.class);
    private static final String DEFAULT_MIME = "application/octet-stream";

    private final S3Client s3Client;
    private final String bucket;

    public ObjectStorageFileStorageService(
            S3Client s3Client, @Value("${app.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        log.info("ObjectStorageFileStorageService ativo (bucket={})", bucket);
    }

    @Override
    public StoredFile store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Arquivo vazio ou ausente");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String generatedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        String storageKey = normalizeDirectory(directory) + "/" + generatedName;

        String mimeType = file.getContentType() != null ? file.getContentType() : DEFAULT_MIME;

        try {
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .contentType(mimeType)
                            .contentLength(file.getSize())
                            .build();
            s3Client.putObject(
                    request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new FileStorageException(
                    "Falha ao gravar arquivo no armazenamento de objetos: " + storageKey, e);
        }

        return new StoredFile(storageKey, mimeType, file.getSize());
    }

    @Override
    public LoadedFile load(String storageKey) {
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucket).key(storageKey).build();
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request);
            String mimeType =
                    stream.response().contentType() != null
                            ? stream.response().contentType()
                            : DEFAULT_MIME;
            return new LoadedFile(new InputStreamResource(stream), mimeType);
        } catch (NoSuchKeyException e) {
            throw new FileStorageException(
                    "Arquivo não encontrado no armazenamento de objetos: " + storageKey, e);
        } catch (S3Exception e) {
            throw new FileStorageException(
                    "Falha ao ler arquivo do armazenamento de objetos: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            // deleteObject é idempotente no protocolo S3: não falha se a key já não existir.
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (S3Exception e) {
            throw new FileStorageException(
                    "Falha ao remover arquivo do armazenamento de objetos: " + storageKey, e);
        }
    }

    // ── Helpers (mesma semântica do LocalDiskFileStorageService) ──

    private static String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) return "misc";
        return directory.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        return originalFilename.substring(dot + 1).toLowerCase();
    }
}
