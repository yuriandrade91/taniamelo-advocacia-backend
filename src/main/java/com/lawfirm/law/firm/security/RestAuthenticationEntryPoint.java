package com.lawfirm.law.firm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Writes a consistent ApiResponse JSON body (instead of Spring Security's default
 * 403 HTML/empty body) when an unauthenticated request hits a protected endpoint.
 *
 * Uses its own local ObjectMapper instance instead of injecting the app-wide
 * Spring-managed bean: this class only ever serializes a tiny, date-free error
 * payload, so it doesn't need any of the app's Jackson customizations (and stays
 * decoupled from whatever is causing the shared ObjectMapper bean to be
 * unavailable in this project's current auto-configuration setup).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(
                List.of(new ApiError(null, "Autenticação necessária", "UNAUTHENTICATED")));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
