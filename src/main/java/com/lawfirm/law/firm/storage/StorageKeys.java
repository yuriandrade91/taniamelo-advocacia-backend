package com.lawfirm.law.firm.storage;

import java.util.Locale;
import java.util.UUID;

/**
 * Formato da chave de storage, igual para disco local e armazenamento de objetos: {@code
 * <diretório>/<uuid>.<extensão>}. Mantê-lo num lugar só é o que permite trocar de backend sem
 * migrar as chaves já gravadas no banco.
 */
final class StorageKeys {

    private StorageKeys() {}

    /** Nova chave única dentro de {@code directory}, preservando a extensão do arquivo original. */
    static String newKey(String directory, String originalFilename) {
        String extension = extractExtension(originalFilename);
        String generatedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        return normalizeDirectory(directory) + "/" + generatedName;
    }

    private static String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) return "misc";
        return directory.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
