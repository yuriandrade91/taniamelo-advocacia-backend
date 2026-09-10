package com.lawfirm.law.firm.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Prova que a autorização por papel <b>recusa de verdade</b>.
 *
 * <p>O {@code SecuredEndpointsContractTest} garante que a anotação está no método; este garante que
 * ela tem efeito. São coisas diferentes: sem {@code @EnableMethodSecurity}, a anotação compila,
 * fica bonita no código e não recusa nada - o pior estado possível, porque parece protegido.
 */
@DisplayName("Autorização por papel: STAFF opera, ADMIN/LAWYER destrói")
class RoleAuthorizationIntegrationTest extends PostgresIntegrationTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired private WebApplicationContext contexto;

    private MockMvc mockMvc;

    /**
     * MockMvc montado à mão com {@code springSecurity()}: no Boot 4 o {@code @AutoConfigureMockMvc}
     * mudou de módulo, e montar aqui evita mais uma dependência para fazer o que uma linha resolve.
     * Sem o {@code springSecurity()} a cadeia de filtros não entra e todo teste passaria - dizendo
     * que está protegido quando não está.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF não exclui cliente")
    void staffCannotDeleteClient() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF não exclui compromisso")
    void staffCannotDeleteAppointment() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/{id}", ID)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF não restaura")
    void staffCannotRestore() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/{id}/restore", ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/appointments/{id}/restore", ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF não lista os usuários do escritório")
    void staffCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("o 403 vem no envelope padrão, não em HTML")
    void forbiddenUsesTheStandardEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("STAFF continua operando: lista e consulta seguem abertas")
    void staffStillOperates() throws Exception {
        // O corte é entre operar e destruir. Se o dia a dia quebrar, a regra está errada.
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/appointments")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LAWYER")
    @DisplayName("LAWYER passa pela autorização (404 do id inexistente, não 403)")
    void lawyerPassesAuthorization() throws Exception {
        // 404 aqui é sucesso do teste: significa que a requisição chegou ao service e só
        // não achou o registro. 403 significaria que parou antes.
        mockMvc.perform(delete("/api/v1/clients/{id}", ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN passa pela autorização")
    void adminPassesAuthorization() throws Exception {
        mockMvc.perform(delete("/api/v1/appointments/{id}", ID)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
    }
}
