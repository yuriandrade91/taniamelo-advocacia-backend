package com.lawfirm.law.firm.service;

import org.springframework.core.io.Resource;

/**
 * A logo de um escritório pronta para a resposta HTTP. {@code version} é a mesma que vai no {@code
 * v} do {@code logoUrl}: quem pede com ela pode guardar a imagem sem perguntar de novo.
 */
public record TenantLogo(Resource resource, String contentType, String version) {

    public boolean isVersion(String requested) {
        return version.equals(requested);
    }
}
