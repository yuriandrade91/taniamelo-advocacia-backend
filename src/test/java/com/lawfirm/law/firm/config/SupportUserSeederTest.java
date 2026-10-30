package com.lawfirm.law.firm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.SupportUser;
import com.lawfirm.law.firm.repository.SupportUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupportUserSeeder: agente de suporte inicial da plataforma")
class SupportUserSeederTest {

    @Mock private SupportUserRepository supportUserRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private SupportUserSeeder seeder(String email, String password, String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return new SupportUserSeeder(supportUserRepository, passwordEncoder, email, password, env);
    }

    @Test
    @DisplayName("em dev, e-mail e senha vazios caem nos defaults e o agente é criado")
    void blankValuesFallBackToDefaultsInDev() {
        when(supportUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("changeme123")).thenReturn("$2a$10$hash");

        seeder("", "", "dev").run();

        ArgumentCaptor<SupportUser> saved = ArgumentCaptor.forClass(SupportUser.class);
        verify(supportUserRepository).save(saved.capture());
        assertEquals("suporte@plataforma.adv.br", saved.getValue().getEmail());
        assertEquals("$2a$10$hash", saved.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("fora de dev/test, recusa criar o agente com senha vazia ou a de exemplo")
    void refusesExamplePasswordOutsideDev() {
        when(supportUserRepository.count()).thenReturn(0L);

        assertThrows(
                IllegalStateException.class,
                () -> seeder("s@x.com", "changeme123", "postgres").run());
        assertThrows(IllegalStateException.class, () -> seeder("s@x.com", "", "postgres").run());

        verify(supportUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("fora de dev/test, agente já existente sobe mesmo sem senha definida")
    void existingAgentBootsWithoutPasswordOutsideDev() {
        when(supportUserRepository.count()).thenReturn(1L);

        seeder("s@x.com", "", "postgres").run();

        verify(supportUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("fora de dev/test, senha real cria o agente normalmente")
    void realPasswordSeedsOutsideDev() {
        when(supportUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("senha-forte-de-verdade")).thenReturn("$2a$10$hash");

        seeder("s@x.com", "senha-forte-de-verdade", "postgres").run();

        verify(supportUserRepository).save(any());
    }
}
