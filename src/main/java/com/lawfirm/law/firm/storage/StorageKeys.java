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
        return trimSlashes(directory.replace('\\', '/'));
    }

    // Sem regex: "/+$" volta atrás a partir de cada barra e fica quadrático numa sequência longa
    // de barras que não termina a string.
    private static String trimSlashes(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') start++;
        while (end > start && path.charAt(end - 1) == '/') end--;
        return path.substring(start, end);
    }

    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
