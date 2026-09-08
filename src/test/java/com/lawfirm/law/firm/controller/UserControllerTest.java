package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController: consulta de usuários para resolver autoria")
class UserControllerTest {

    @Mock private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new UserController(userRepository))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    private static User user(String fullName, String username, Role role, boolean active) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(username + "@taniamelo.adv.br");
        user.setPasswordHash("nao-deve-vazar");
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    @Test
    @DisplayName("lista no envelope padrão, ordenada por nome")
    void listsUsersOrderedByName() throws Exception {
        when(userRepository.findAll(any(Sort.class)))
                .thenReturn(
                        List.of(
                                user("Ana Prado", "ana", Role.LAWYER, true),
                                user("Tânia Melo", "tania", Role.ADMIN, true)));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("Ana Prado"))
                .andExpect(jsonPath("$.data[0].role").value("LAWYER"));
    }

    @Test
    @DisplayName("não expõe e-mail nem hash de senha")
    void neverLeaksCredentials() throws Exception {
        when(userRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(user("Tânia Melo", "tania", Role.ADMIN, true)));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("por padrão esconde desativados")
    void hidesInactiveByDefault() throws Exception {
        when(userRepository.findAll(any(Sort.class)))
                .thenReturn(
                        List.of(
                                user("Ana Prado", "ana", Role.LAWYER, true),
                                user("Ex Estagiário", "ex", Role.STAFF, false)));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Ana Prado"));
    }

    @Test
    @DisplayName("includeInactive=true traz quem já saiu, para resolver autoria antiga")
    void includeInactiveBringsFormerUsers() throws Exception {
        when(userRepository.findAll(any(Sort.class)))
                .thenReturn(
                        List.of(
                                user("Ana Prado", "ana", Role.LAWYER, true),
                                user("Ex Estagiário", "ex", Role.STAFF, false)));

        mockMvc.perform(get("/api/v1/users").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].active").value(false));
    }
}
