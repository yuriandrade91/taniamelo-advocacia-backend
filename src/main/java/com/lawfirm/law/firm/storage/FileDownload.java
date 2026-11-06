package com.lawfirm.law.firm.storage;

import org.springframework.core.io.Resource;

/** Tudo que um controller precisa para montar a resposta HTTP de um download. */
public record FileDownload(
        Resource resource, String mimeType, String originalFilename, long sizeBytes) {}
