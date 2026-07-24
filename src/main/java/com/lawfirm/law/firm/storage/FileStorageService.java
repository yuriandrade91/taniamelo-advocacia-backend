package com.lawfirm.law.firm.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstração de armazenamento de arquivos (documentos do cliente, PDFs de simulação de CNIS). Duas
 * implementações, escolhidas por {@code app.storage.type}: {@link LocalDiskFileStorageService}
 * (disco local, montado como volume Docker) e {@link ObjectStorageFileStorageService}
 * (armazenamento de objetos via protocolo S3 - AWS, R2, MinIO). Trocar entre elas é só mudar a
 * configuração - nenhuma entidade, DTO, service ou controller muda, todos dependem só desta
 * interface e do {@code storageKey} opaco que ela devolve.
 */
public interface FileStorageService {

    /**
     * Grava o arquivo enviado e devolve a chave opaca para recuperá-lo depois.
     *
     * @param file arquivo recebido no multipart
     * @param directory subpasta lógica (ex.: "clients/42/documents") - cada implementação decide
     *     como isso vira caminho/prefixo real
     */
    StoredFile store(MultipartFile file, String directory);

    /** Carrega o arquivo gravado sob a chave devolvida por {@link #store}. */
    LoadedFile load(String storageKey);

    /** Remove o arquivo do storage. Idempotente: não falha se já não existir. */
    void delete(String storageKey);
}
