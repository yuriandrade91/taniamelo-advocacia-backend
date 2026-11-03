package com.lawfirm.law.firm.dto;

/**
 * Dados PÚBLICOS mínimos de um tenant, usados antes do login (o front descobre o {@code tenantId} a
 * partir do slug do subdomínio, para mandar no cabeçalho X-Tenant-Id). Não expõe CNPJ, plano, etc.
 *
 * <p>{@code logoUrl} é nulo enquanto o escritório não enviou logo: o front mostra a marca padrão
 * sem pedir imagem nenhuma.
 */
public record TenantPublicDTO(String tenantId, String slug, String razaoSocial, String logoUrl) {}
