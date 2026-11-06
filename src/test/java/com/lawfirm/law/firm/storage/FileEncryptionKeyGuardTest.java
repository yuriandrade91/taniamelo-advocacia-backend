package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("FileEncryptionKeyGuard: a chave de dev não cifra documento de verdade")
class FileEncryptionKeyGuardTest {

    private static final String PROPRIA = "cHJvcHJpYS1kby1lc2NyaXRvcmlvLTMyLWJ5dGVzISE=";

    private static MockEnvironment perfil(String... perfis) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfis);
        return env;
    }

    @Test
    @DisplayName("a chave de dev é aceita em dev e em test")
    void devKeyAllowedLocally() {
        String dev = FileEncryptionKeyGuard.DEV_KEY;
        assertDoesNotThrow(() -> new FileEncryptionKeyGuard(dev, perfil("dev")));
        assertDoesNotThrow(() -> new FileEncryptionKeyGuard(dev, perfil("test")));
    }

    @Test
    @DisplayName("a chave de dev fora de dev/test recusa a subida")
    void devKeyRefusedElsewhere() {
        MockEnvironment env = perfil("postgres");
        assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyGuard(FileEncryptionKeyGuard.DEV_KEY, env));
    }

    @Test
    @DisplayName("sem chave, fora de dev/test, também recusa")
    void blankKeyRefusedElsewhere() {
        MockEnvironment env = perfil("postgres");
        assertThrows(IllegalStateException.class, () -> new FileEncryptionKeyGuard(" ", env));
    }

    @Test
    @DisplayName("sem perfil ativo vale a regra de produção")
    void noProfileIsProduction() {
        MockEnvironment env = perfil();
        assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyGuard(FileEncryptionKeyGuard.DEV_KEY, env));
    }

    @Test
    @DisplayName("chave própria sobe em qualquer perfil")
    void ownKeyAllowed() {
        assertDoesNotThrow(() -> new FileEncryptionKeyGuard(PROPRIA, perfil("postgres")));
    }
}
