package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.storage.FileStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

/**
 * Confere a imagem enviada como logo pelos primeiros bytes, não pelo Content-Type do multipart, que
 * é o que o navegador declarou. A logo sai numa rota pública e no topo de toda tela: um HTML com
 * nome de .png não pode passar. SVG fica de fora de propósito, porque é documento e pode levar
 * script.
 */
final class LogoImage {

    /** Marca, não documento: 2 MB sobram para um PNG nítido no tamanho do cabeçalho. */
    static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final String FIELD = "file";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private LogoImage() {}

    /** O tipo real da imagem; recusa com 400 o que não for PNG, JPEG ou WebP de até 2 MB. */
    static String contentTypeOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(
                    FIELD, ValidationErrorCode.REQUIRED_FIELD, "Envie a imagem da logo.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException(
                    FIELD, ValidationErrorCode.FILE_TOO_LARGE, "A logo pode ter até 2 MB.");
        }
        byte[] head = head(file);
        if (startsWith(head, 0, PNG)) return "image/png";
        if (startsWith(head, 0, JPEG)) return "image/jpeg";
        if (startsWith(head, 0, RIFF) && startsWith(head, 8, WEBP)) return "image/webp";
        throw new ValidationException(
                FIELD,
                ValidationErrorCode.INVALID_ENUM_VALUE,
                "A logo precisa ser uma imagem PNG, JPEG ou WebP.");
    }

    private static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException ex) {
            throw new FileStorageException("Não foi possível ler a imagem enviada", ex);
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        int end = offset + prefix.length;
        return data.length >= end && Arrays.equals(data, offset, end, prefix, 0, prefix.length);
    }
}
