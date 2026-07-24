package com.lawfirm.law.firm.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Exposes a {@link CorsConfigurationSource} consumed by {@code SecurityConfig#securityFilterChain}
 * via {@code http.cors(...)}. Deliberately NOT a standalone {@code CorsFilter} bean: a plain filter
 * bean registers after Spring Security's chain (default servlet filter order), so a preflight
 * OPTIONS request to any protected endpoint gets rejected with 401 by the authorization filter
 * before the CORS headers are ever added - the browser then reports it as a CORS/network failure.
 * Wiring this source into http.cors(...) instead lets Spring Security run its CORS handling as the
 * very first filter, ahead of authentication/authorization.
 *
 * <p>As origens permitidas vêm de {@code app.cors.allowed-origins} (lista separada por vírgula). O
 * padrão é {@code *} para não travar o desenvolvimento local; na AWS/produção, defina a variável
 * {@code APP_CORS_ALLOWED_ORIGINS} com o domínio real do frontend (ex.: {@code
 * https://app.taniamelo.adv.br}). Quando o valor é {@code *} usa-se {@code allowedOriginPatterns}
 * (obrigatório junto de {@code allowCredentials=true}); com origens explícitas usa-se {@code
 * allowedOrigins}.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String allowedOriginsCsv) {
        this.allowedOrigins =
                Arrays.stream(allowedOriginsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Permite credenciais (cookies, header Authorization).
        config.setAllowCredentials(true);

        boolean wildcard = allowedOrigins.isEmpty() || allowedOrigins.contains("*");
        if (wildcard) {
            // allowedOriginPatterns aceita "*" mesmo com allowCredentials=true (allowedOrigins
            // não).
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(allowedOrigins);
        }

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
