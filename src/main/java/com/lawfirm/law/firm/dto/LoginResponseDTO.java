package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class LoginResponseDTO {

    private String token;
    private String tokenType = "Bearer";
    private long expiresInSeconds;
    private String fullName;
    private String email;
    private String role;

    @Schema(
            description = "Identificador público (opaco) do tenant - UUID canônico",
            example = "3f1a7c2e-9b40-4a1e-8a2b-0d5f6c7e8a90")
    private String tenantId;

    @Schema(description = "Slug amigável do tenant (subdomínio/URL)", example = "tania")
    private String tenantSlug;

    public LoginResponseDTO() {}

    public LoginResponseDTO(
            String token, long expiresInSeconds, String fullName, String email, String role) {
        this.token = token;
        this.expiresInSeconds = expiresInSeconds;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantSlug() {
        return tenantSlug;
    }

    public void setTenantSlug(String tenantSlug) {
        this.tenantSlug = tenantSlug;
    }
}
