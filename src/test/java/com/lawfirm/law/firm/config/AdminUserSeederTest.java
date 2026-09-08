package com.lawfirm.law.firm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import com.lawfirm.law.firm.tenant.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminUserSeeder: ADMIN inicial por schema de tenant")
class AdminUserSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private TenancyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TenancyProperties();
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private AdminUserSeeder seeder() {
        return new AdminUserSeeder(
                userRepository,
                passwordEncoder,
                properties,
                "admin@taniamelo.adv.br",
                "changeme123");
    }

    @Test
    @DisplayName("cria um ADMIN em cada schema que estiver sem usuários")
    void seedsEveryEmptySchema() {
        when(userRepository.count()).thenReturn(0L);

        seeder().run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(saved.capture());

        User admin = saved.getAllValues().get(0);
        assertEquals("Administrador", admin.getFullName());
        assertEquals("admin@taniamelo.adv.br", admin.getEmail());
        assertEquals("admin", admin.getUsername(), "username derivado do e-mail");
        assertEquals("$2a$10$hash", admin.getPasswordHash());
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.getActive());
        verify(passwordEncoder, times(2)).encode("changeme123");
    }

    @Test
    @DisplayName("schema que já tem usuários é deixado como está")
    void doesNotSeedPopulatedSchemas() {
        when(userRepository.count()).thenReturn(3L);

        seeder().run();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("semeia só onde faltar quando os schemas estão em estados diferentes")
    void seedsOnlyTheEmptyOnes() {
        when(userRepository.count()).thenReturn(0L).thenReturn(5L);

        seeder().run();

        verify(userRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("o TenantContext é limpo depois de percorrer todos os schemas")
    void clearsTenantContextAfterwards() {
        when(userRepository.count()).thenReturn(1L);

        seeder().run();

        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("e-mail sem @ vira o próprio username")
    void emailWithoutAtSignBecomesTheUsername() {
        when(userRepository.count()).thenReturn(0L);
        properties.setSchemas(List.of("tenant_tania"));

        new AdminUserSeeder(userRepository, passwordEncoder, properties, "raiz", "senha").run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("raiz", saved.getValue().getUsername());
    }

    @Test
    @DisplayName("nenhum schema configurado significa nada a semear")
    void noSchemasMeansNoWork() {
        properties.setSchemas(List.of());

        seeder().run();

        verify(userRepository, never()).count();
        verify(userRepository, never()).save(any());
    }
}
