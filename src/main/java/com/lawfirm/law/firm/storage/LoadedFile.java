package com.lawfirm.law.firm.storage;

import org.springframework.core.io.Resource;

/**
 * Resultado de um {@link FileStorageService#load}: o {@link Resource} pronto para ser escrito na
 * resposta HTTP, mais o mime type para o header Content-Type (o tamanho/nome original já estão nos
 * metadados do banco).
 */
public record LoadedFile(Resource resource, String mimeType) {}
