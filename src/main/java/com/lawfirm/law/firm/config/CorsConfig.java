package com.lawfirm.law.firm.config;

import java.util.List;
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
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Allow all origins (development). Change to specific origins in production.
        config.setAllowedOriginPatterns(List.of("*"));

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
