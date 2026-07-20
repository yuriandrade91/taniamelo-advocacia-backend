package com.lawfirm.law.firm.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Falha ao gravar/ler/apagar um arquivo no storage configurado (disco local
 * hoje, S3 amanhã). Separada de {@code ValidationException} porque não é erro
 * de input do usuário - é uma falha de infraestrutura (disco cheio, permissão,
 * arquivo já removido por fora, etc.).
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FileStorageException extends RuntimeException {
    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
