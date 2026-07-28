package com.lawfirm.law.firm.dto;

/**
 * Dados PÚBLICOS mínimos de um tenant, usados antes do login (o front descobre o {@code tenantId} a
 * partir do slug do subdomínio, para mandar no cabeçalho X-Tenant-Id). Não expõe CNPJ, plano, etc.
 */
public record TenantPublicDTO(String tenantId, String slug, String razaoSocial) {}
