package com.lawfirm.law.firm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Autenticação stateless via JWT. Autorização por papel (@PreAuthorize) fica para uma próxima fase
 * - hoje qualquer usuário autenticado acessa os endpoints de negócio.
 *
 * <p>Não construímos um DaoAuthenticationProvider manualmente: com um bean PasswordEncoder e um
 * bean UserDetailsService (CustomUserDetailsService) no contexto, o próprio Spring Security monta o
 * provider automaticamente ao criar o AuthenticationManager via AuthenticationConfiguration.
 *
 * <p>CORS usa o CorsConfigurationSource de CorsConfig (mantido aberto propositalmente enquanto o
 * time trabalha em ambiente local - ver docs/ROADMAP.md), plugado aqui via http.cors(...) - e não
 * como um CorsFilter bean solto - para que a resolução de CORS rode antes da autorização. Preflight
 * OPTIONS também é liberado explicitamente como reforço, já que qualquer endpoint protegido por
 * anyRequest().authenticated() rejeitaria o preflight com 401 (sem headers de CORS) antes do
 * browser sequer tentar a requisição real.
 */
@Configuration
@EnableWebSecurity
/*
 * Liga a autorização por método (@PreAuthorize / @RequerAdvogado). Sem isto as anotações são
 * decoração: compilam, ficam no código e não recusam nada - que é o pior estado possível, porque
 * parece protegido.
 */
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final CorsConfigurationSource corsConfigurationSource;
    private final com.lawfirm.law.firm.tenant.TenantResolutionFilter tenantResolutionFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource,
            com.lawfirm.law.firm.tenant.TenantResolutionFilter tenantResolutionFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.corsConfigurationSource = corsConfigurationSource;
        this.tenantResolutionFilter = tenantResolutionFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Preflight requests never carry credentials; permitting
                                        // them here is
                                        // what actually fixes the 401-with-no-CORS-headers failure
                                        // - http.cors(...)
                                        // alone only positions the filter early, it doesn't exempt
                                        // OPTIONS from
                                        // anyRequest().authenticated() below.
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/auth/**")
                                        .permitAll()
                                        // Resolução pública de tenant por slug (pré-login).
                                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants/resolve")
                                        .permitAll()
                                        // Docs are public (just the API shape, no business data);
                                        // the actual
                                        // operations behind them still require a bearer token to
                                        // execute.
                                        .requestMatchers(
                                                "/api/docs/**",
                                                "/api/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/webjars/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/health", "/actuator/info")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Resolve o tenant (schema) antes de qualquer acesso ao banco - inclusive antes do
                // JwtAuthenticationFilter, que carrega o usuário do schema correto.
                .addFilterBefore(tenantResolutionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
