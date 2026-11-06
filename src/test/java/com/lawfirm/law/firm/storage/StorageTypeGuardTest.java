package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("StorageTypeGuard: onde os arquivos dos clientes podem ir")
class StorageTypeGuardTest {

    private static MockEnvironment perfil(String... perfis) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfis);
        return env;
    }

    @Test
    @DisplayName("disco local é aceito em dev e em test")
    void localAllowedInDevAndTest() {
        assertDoesNotThrow(() -> new StorageTypeGuard("local", "", perfil("dev")));
        assertDoesNotThrow(() -> new StorageTypeGuard("local", "", perfil("test")));
    }

    @Test
    @DisplayName("disco local fora de dev/test recusa a subida")
    void localRefusedOutsideDev() {
        MockEnvironment env = perfil("postgres");
        assertThrows(IllegalStateException.class, () -> new StorageTypeGuard("local", "", env));
    }

    @Test
    @DisplayName("sem perfil ativo também recusa: não há default seguro")
    void localRefusedWithoutProfile() {
        MockEnvironment env = perfil();
        assertThrows(IllegalStateException.class, () -> new StorageTypeGuard("local", "", env));
    }

    @Test
    @DisplayName("s3 com bucket sobe em qualquer perfil")
    void s3WithBucketAllowed() {
        assertDoesNotThrow(() -> new StorageTypeGuard("s3", "bucket-x", perfil("postgres")));
    }

    @Test
    @DisplayName("s3 sem bucket recusa, mesmo em dev")
    void s3WithoutBucketRefused() {
        MockEnvironment env = perfil("dev");
        assertThrows(IllegalStateException.class, () -> new StorageTypeGuard("s3", " ", env));
    }
}
