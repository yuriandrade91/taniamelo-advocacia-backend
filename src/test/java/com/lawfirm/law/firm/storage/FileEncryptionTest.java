package com.lawfirm.law.firm.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("FileEncryption: conteúdo dos arquivos de cliente cifrado em AES-256-GCM")
class FileEncryptionTest {

    private static final String KEY = FileEncryptionKeyGuard.DEV_KEY;
    private static final String OTHER_KEY =
            Base64.getEncoder().encodeToString("outra-chave-de-32-bytes-de-teste".getBytes());
    private static final byte[] PDF =
            "%PDF-1.7 laudo pericial de Maria".getBytes(StandardCharsets.UTF_8);

    private final FileEncryption encryption = new FileEncryption(KEY, 1);

    @Test
    @DisplayName("decifrar devolve exatamente o que foi cifrado")
    void roundTrip() {
        assertArrayEquals(PDF, encryption.decrypt(encryption.encrypt(PDF)));
    }

    @Test
    @DisplayName("o conteúdo gravado não contém o original e começa pela marca do formato")
    void storedBytesHideTheContent() {
        byte[] stored = encryption.encrypt(PDF);

        assertTrue(FileEncryption.isEncrypted(stored));
        String asText = new String(stored, StandardCharsets.ISO_8859_1);
        assertFalse(asText.contains("%PDF"));
        assertFalse(asText.contains("Maria"));
    }

    @Test
    @DisplayName(
            "o mesmo arquivo cifrado duas vezes gera bytes diferentes (chave e IV por arquivo)")
    void sameContentEncryptsDifferently() {
        assertFalse(Arrays.equals(encryption.encrypt(PDF), encryption.encrypt(PDF)));
    }

    @Test
    @DisplayName("arquivo vazio também faz a ida e a volta")
    void emptyContent() {
        assertArrayEquals(new byte[0], encryption.decrypt(encryption.encrypt(new byte[0])));
    }

    @ParameterizedTest(name = "byte {0} alterado")
    @ValueSource(ints = {4, 10, 40, 70, 80, -1})
    @DisplayName("qualquer byte alterado (versão, chave do arquivo, IV, conteúdo, tag) é recusado")
    void tamperingIsDetected(int position) {
        byte[] stored = encryption.encrypt(PDF);
        int index = position < 0 ? stored.length - 1 : position;
        stored[index] ^= 0x01;

        assertThrows(FileStorageException.class, () -> encryption.decrypt(stored));
    }

    @Test
    @DisplayName("outra chave-mestra não decifra: falha, em vez de devolver bytes sem sentido")
    void wrongKeyFails() {
        byte[] stored = encryption.encrypt(PDF);
        FileEncryption other = new FileEncryption(OTHER_KEY, 1);

        FileStorageException ex =
                assertThrows(FileStorageException.class, () -> other.decrypt(stored));
        assertTrue(ex.getMessage().contains("chave errada"));
    }

    @Test
    @DisplayName("arquivo de outra versão da chave é recusado nomeando as duas versões")
    void otherKeyVersionFails() {
        byte[] stored = new FileEncryption(KEY, 2).encrypt(PDF);

        FileStorageException ex =
                assertThrows(FileStorageException.class, () -> encryption.decrypt(stored));
        assertTrue(ex.getMessage().contains("versão 2"));
        assertTrue(ex.getMessage().contains("versão 1"));
    }

    @Test
    @DisplayName("conteúdo sem a marca ou truncado não é tratado como cifrado")
    void notEncryptedContent() {
        assertFalse(FileEncryption.isEncrypted(PDF));
        assertFalse(FileEncryption.isEncrypted(new byte[] {'T', 'M'}));
        assertThrows(FileStorageException.class, () -> encryption.decrypt(PDF));

        byte[] truncated = Arrays.copyOf(encryption.encrypt(PDF), 20);
        assertThrows(FileStorageException.class, () -> encryption.decrypt(truncated));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "não é base64", "AAAAAAAAAAAAAAAAAAAAAA=="})
    @DisplayName("chave ausente, inválida ou menor que 32 bytes recusa a subida")
    void invalidKeyRefused(String key) {
        assertThrows(IllegalStateException.class, () -> new FileEncryption(key, 1));
    }

    @ParameterizedTest(name = "versão {0}")
    @ValueSource(ints = {0, 256, -1})
    @DisplayName("versão da chave fora de 1 a 255 recusa a subida (ela ocupa um byte)")
    void invalidVersionRefused(int version) {
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> new FileEncryption(KEY, version));
        assertTrue(ex.getMessage().contains(String.valueOf(version)));
    }

    @Test
    @DisplayName("a versão 255 cabe no cabeçalho e volta como 255")
    void highestVersionRoundTrips() {
        FileEncryption v255 = new FileEncryption(KEY, 255);
        byte[] stored = v255.encrypt(PDF);

        assertEquals(255, Byte.toUnsignedInt(stored[4]));
        assertArrayEquals(PDF, v255.decrypt(stored));
    }
}
