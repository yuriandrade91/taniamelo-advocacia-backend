package com.lawfirm.law.firm.exception;

/**
 * Recurso inexistente (HTTP 404). Erro de negócio "esperado" - tratado pela central de erros
 * (GlobalExceptionHandler), nunca logado como erro de sistema.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " não encontrado(a) com id: " + id);
    }
}
