package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ChangePasswordRequestDTO;
import com.lawfirm.law.firm.dto.MyProfileUpdateRequestDTO;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * O perfil do próprio usuário.
 *
 * <p>O que estes testes protegem não é o caminho feliz: é o conjunto de recusas. Trocar senha sem
 * confirmar a atual entrega a conta a quem passar por um computador destravado; trocar pela mesma
 * senha responde 200 sem trocar nada, e quem fez isso por desconfiar de acesso indevido sai achando
 * que resolveu.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Meus dados: o que o próprio usuário pode mudar")
class MyProfileServiceTest {

    private static final UUID EU = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;

    private MyProfileService service;
    private User eu;

    @BeforeEach
    void setUp() {
        service = new MyProfileService(repository, passwordEncoder);

        eu = new User();
        eu.setId(EU);
        eu.setFullName("Tania Melo");
        eu.setEmail("dra.tania@taniamelo.adv.br");
        eu.setUsername("dra.tania");
        eu.setPasswordHash("$hash-atual");
        eu.setRole(Role.ADMIN);
        eu.setActive(true);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new UserPrincipal(eu), null, java.util.List.of()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("lê os próprios dados, com e-mail e sem hash de senha")
    void leOsProprios() {
        when(repository.findById(EU)).thenReturn(Optional.of(eu));

        var dto = service.meusDados();

        assertEquals("dra.tania@taniamelo.adv.br", dto.getEmail());
        assertEquals(Role.ADMIN, dto.getRole());
        assertEquals("dra.tania", dto.getUsername());
    }

    @Test
    @DisplayName("e-mail já usado por outra pessoa é recusado nomeando o campo")
    void emailDuplicado() {
        User outra = new User();
        outra.setId(UUID.randomUUID());

        when(repository.findById(EU)).thenReturn(Optional.of(eu));
        when(repository.findByEmailIgnoreCase("ana.souza@taniamelo.adv.br"))
                .thenReturn(Optional.of(outra));

        MyProfileUpdateRequestDTO dto = new MyProfileUpdateRequestDTO();
        dto.setFullName("Tania Melo");
        dto.setEmail("ana.souza@taniamelo.adv.br");

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service.atualizar(dto));
        assertEquals("email", ex.getField());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("manter o próprio e-mail não conta como duplicado")
    void manterOProprioEmail() {
        when(repository.findById(EU)).thenReturn(Optional.of(eu));
        when(repository.findByEmailIgnoreCase(eu.getEmail())).thenReturn(Optional.of(eu));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        MyProfileUpdateRequestDTO dto = new MyProfileUpdateRequestDTO();
        dto.setFullName("Tânia Melo Advogada");
        dto.setEmail(eu.getEmail());

        assertEquals("Tânia Melo Advogada", service.atualizar(dto).getFullName());
    }

    @Test
    @DisplayName("senha atual errada não troca nada")
    void senhaAtualErrada() {
        when(repository.findById(EU)).thenReturn(Optional.of(eu));
        when(passwordEncoder.matches("chute", "$hash-atual")).thenReturn(false);

        ChangePasswordRequestDTO dto = new ChangePasswordRequestDTO();
        dto.setCurrentPassword("chute");
        dto.setNewPassword("senha-nova-forte");

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service.trocarSenha(dto));
        assertEquals("currentPassword", ex.getField());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("trocar pela MESMA senha é recusado — senão responde 200 sem trocar nada")
    void mesmaSenha() {
        when(repository.findById(EU)).thenReturn(Optional.of(eu));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);

        ChangePasswordRequestDTO dto = new ChangePasswordRequestDTO();
        dto.setCurrentPassword("a-mesma");
        dto.setNewPassword("a-mesma");

        ValidationException ex =
                assertThrows(ValidationException.class, () -> service.trocarSenha(dto));
        assertEquals("newPassword", ex.getField());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("senha correta grava o hash novo, nunca o texto")
    void trocaComSucesso() {
        when(repository.findById(EU)).thenReturn(Optional.of(eu));
        when(passwordEncoder.matches("a-atual", "$hash-atual")).thenReturn(true);
        when(passwordEncoder.matches("senha-nova-forte", "$hash-atual")).thenReturn(false);
        when(passwordEncoder.encode("senha-nova-forte")).thenReturn("$hash-novo");
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangePasswordRequestDTO dto = new ChangePasswordRequestDTO();
        dto.setCurrentPassword("a-atual");
        dto.setNewPassword("senha-nova-forte");

        service.trocarSenha(dto);

        assertEquals("$hash-novo", eu.getPasswordHash());
    }
}
