package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
}
