package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

@DisplayName("RestAuthenticationEntryPoint: 401 no envelope padrão em vez de HTML do Spring")
class RestAuthenticationEntryPointTest {

    @Test
    @DisplayName("escreve 401 JSON com código UNAUTHENTICATED")
    void writesJsonEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint()
                .commence(
                        new MockHttpServletRequest("GET", "/api/v1/clients"),
                        response,
                        new BadCredentialsException("sem token"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(false, body.get("success").asBoolean());
        assertTrue(body.get("data").isArray());
        assertEquals(0, body.get("data").size());
        assertEquals("UNAUTHENTICATED", body.get("errors").get(0).get("code").asText());
        assertEquals("Autenticação necessária", body.get("errors").get(0).get("message").asText());
        assertTrue(body.get("errors").get(0).get("field").isNull());
    }

    @Test
    @DisplayName("o corpo não vaza a mensagem interna da exceção de autenticação")
    void doesNotLeakInternalMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint()
                .commence(
                        new MockHttpServletRequest(),
                        response,
                        new BadCredentialsException("senha errada do usuário admin@x.com"));

        assertEquals(-1, response.getContentAsString().indexOf("admin@x.com"));
    }
}
