package com.lawfirm.law.firm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@DisplayName("CorsConfig: origens permitidas via app.cors.allowed-origins")
class CorsConfigTest {

    private CorsConfiguration configFor(String csv) {
        CorsConfigurationSource source = new CorsConfig(csv).corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/clients");
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);
        return config;
    }

    @Test
    @DisplayName("\"*\" usa allowedOriginPatterns (exigido junto de allowCredentials)")
    void wildcardUsesPatterns() {
        CorsConfiguration config = configFor("*");

        assertEquals(List.of("*"), config.getAllowedOriginPatterns());
        assertNull(config.getAllowedOrigins());
        assertEquals(Boolean.TRUE, config.getAllowCredentials());
    }

    @Test
    @DisplayName("origem vazia também cai no comportamento curinga")
    void emptyValueFallsBackToWildcard() {
        assertEquals(List.of("*"), configFor("").getAllowedOriginPatterns());
        assertEquals(List.of("*"), configFor("   ").getAllowedOriginPatterns());
        assertEquals(List.of("*"), configFor(",,,").getAllowedOriginPatterns());
    }

    @Test
    @DisplayName("origens explícitas usam allowedOrigins")
    void explicitOriginsUseAllowedOrigins() {
        CorsConfiguration config =
                configFor("https://app.taniamelo.adv.br,https://admin.taniamelo.adv.br");

        assertEquals(
                List.of("https://app.taniamelo.adv.br", "https://admin.taniamelo.adv.br"),
                config.getAllowedOrigins());
        assertNull(config.getAllowedOriginPatterns());
    }

    @Test
    @DisplayName("espaços e itens vazios na lista são descartados")
    void trimsAndDropsEmptyEntries() {
        CorsConfiguration config = configFor("  https://a.com , , https://b.com  ");
        assertEquals(List.of("https://a.com", "https://b.com"), config.getAllowedOrigins());
    }

    @Test
    @DisplayName("qualquer \"*\" na lista força o modo curinga")
    void anyWildcardInTheListWins() {
        assertEquals(List.of("*"), configFor("https://a.com,*").getAllowedOriginPatterns());
    }

    @Test
    @DisplayName("todos os métodos e headers são liberados")
    void allowsAllMethodsAndHeaders() {
        CorsConfiguration config = configFor("https://a.com");
        assertEquals(List.of("*"), config.getAllowedHeaders());
        assertEquals(List.of("*"), config.getAllowedMethods());
    }

    @Test
    @DisplayName("a configuração vale para qualquer rota")
    void appliesToEveryPath() {
        CorsConfigurationSource source = new CorsConfig("*").corsConfigurationSource();
        assertNotNull(
                source.getCorsConfiguration(
                        new MockHttpServletRequest("POST", "/api/v1/auth/login")));
        assertNotNull(
                source.getCorsConfiguration(new MockHttpServletRequest("GET", "/actuator/health")));
        assertTrue(true);
    }
}
