package com.lawfirm.law.firm.storage;

/**
 * Resultado de um {@link FileStorageService#store}: tudo que a camada de
 * serviço precisa persistir no banco (nunca os bytes do arquivo em si).
 */
public record StoredFile(String storageKey, String mimeType, long sizeBytes) {
}
