package com.lawfirm.law.firm.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementação em disco local do {@link FileStorageService}. Grava sob um diretório base
 * configurável ({@code app.storage.local.base-path}), que em docker-compose é montado como volume
 * nomeado para não perder os arquivos a cada rebuild da imagem. O {@code storageKey} devolvido é o
 * caminho relativo ao diretório base (nunca o caminho absoluto do host) - é isso que fica gravado
 * no banco.
 */
@Service
public class LocalDiskFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskFileStorageService.class);

    private final Path basePath;

    public LocalDiskFileStorageService(
            @Value("${app.storage.local.base-path:./storage}") String basePathConfig) {
        this.basePath = Path.of(basePathConfig).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new FileStorageException(
                    "Não foi possível criar o diretório de storage: " + this.basePath, e);
        }
        log.info("LocalDiskFileStorageService usando base-path={}", this.basePath);
    }

    @Override
    public StoredFile store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Arquivo vazio ou ausente");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String generatedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        String storageKey = normalizeDirectory(directory) + "/" + generatedName;

        Path target = resolveWithinBase(storageKey);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new FileStorageException("Falha ao gravar arquivo no storage: " + storageKey, e);
        }

        String mimeType =
                file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        return new StoredFile(storageKey, mimeType, file.getSize());
    }

    @Override
    public LoadedFile load(String storageKey) {
        Path target = resolveWithinBase(storageKey);
        if (!Files.exists(target)) {
            throw new FileStorageException("Arquivo não encontrado no storage: " + storageKey);
        }
        return new LoadedFile(new FileSystemResource(target), probeMimeType(target));
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinBase(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new FileStorageException("Falha ao remover arquivo do storage: " + storageKey, e);
        }
    }

    // ── Helpers ──

    /**
     * Resolve a chave dentro do diretório base, recusando qualquer tentativa de path traversal
     * (`..`).
     */
    private Path resolveWithinBase(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new FileStorageException("Chave de storage inválida: " + storageKey);
        }
        return resolved;
    }

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

    private static String probeMimeType(Path path) {
        try {
            String probed = Files.probeContentType(path);
            return probed != null ? probed : "application/octet-stream";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
