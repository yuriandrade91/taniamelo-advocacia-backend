package com.lawfirm.law.firm.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * O cookie httpOnly que carrega o refresh token: nome, path e flags num lugar só, para emitir,
 * limpar e ler com a mesma configuração. Em produção, {@code secure=true} e {@code SameSite=None}
 * vêm das propriedades {@code app.auth.refresh-cookie.*}.
 */
@Component
public class RefreshTokenCookie {

    private final String name;
    private final String path;
    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookie(
            @Value("${app.auth.refresh-cookie.name:refreshToken}") String name,
            @Value("${app.auth.refresh-cookie.path:/api/v1/auth}") String path,
            @Value("${app.auth.refresh-cookie.secure:false}") boolean secure,
            @Value("${app.auth.refresh-cookie.same-site:Lax}") String sameSite) {
        this.name = name;
        this.path = path;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie issue(String value, long maxAgeSeconds) {
        return build(value, maxAgeSeconds);
    }

    /** Mesmo nome e path do emitido, com maxAge 0: é assim que o navegador apaga. */
    public ResponseCookie cleared() {
        return build("", 0);
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
