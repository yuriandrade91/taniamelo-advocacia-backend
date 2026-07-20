package com.lawfirm.law.firm.dto;

public class LoginResponseDTO {

    private String token;
    private String tokenType = "Bearer";
    private long expiresInSeconds;
    private String fullName;
    private String email;
    private String role;

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
}
