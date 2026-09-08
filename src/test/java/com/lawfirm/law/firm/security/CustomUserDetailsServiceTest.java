package com.lawfirm.law.firm.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService: login por e-mail OU username")
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private CustomUserDetailsService service;

    @Test
    @DisplayName("o mesmo valor é buscado como e-mail e como username")
    void searchesBothEmailAndUsernameWithTheSameValue() {
        User user = TestFixtures.user();
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("dra.tania", "dra.tania"))
                .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("dra.tania");

        assertEquals(user.getEmail(), details.getUsername());
        assertTrue(details instanceof UserPrincipal);
        assertEquals(user.getId(), ((UserPrincipal) details).getId());
        verify(userRepository)
                .findByEmailIgnoreCaseOrUsernameIgnoreCase(eq("dra.tania"), eq("dra.tania"));
    }

    @Test
    @DisplayName("usuário inexistente vira UsernameNotFoundException com o login na mensagem")
    void unknownLoginThrows() {
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("fantasma", "fantasma"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException ex =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> service.loadUserByUsername("fantasma"));
        assertTrue(ex.getMessage().contains("fantasma"));
    }

    @Test
    @DisplayName("usuário inativo é carregado, mas com isEnabled=false")
    void inactiveUserIsLoadedButDisabled() {
        User user = TestFixtures.user();
        user.setActive(false);
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase("x", "x"))
                .thenReturn(Optional.of(user));

        assertEquals(false, service.loadUserByUsername("x").isEnabled());
    }
}
