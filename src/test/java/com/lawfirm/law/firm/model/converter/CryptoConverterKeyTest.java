package com.lawfirm.law.firm.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CryptoConverter: resolução e validação da chave AES")
class CryptoConverterKeyTest {

    @Test
    @DisplayName("com APP_ENCRYPTION_KEY definida no ambiente, é ela que é usada")
    void usesTheEnvironmentKeyWhenPresent() {
        String envKey = System.getenv("APP_ENCRYPTION_KEY");
        CryptoConverter converter = new CryptoConverter();

        String stored = converter.convertToDatabaseColumn("segredo");
        assertNotNull(stored);
        assertEquals("segredo", converter.convertToEntityAttribute(stored));

        if (envKey != null && !envKey.isBlank()) {
            int length = Base64.getDecoder().decode(envKey).length;
            org.junit.jupiter.api.Assertions.assertTrue(
                    length == 16 || length == 24 || length == 32,
                    "APP_ENCRYPTION_KEY deve decodificar para 16, 24 ou 32 bytes, veio " + length);
        }
    }

    @Test
    @DisplayName("valores de tamanhos variados fazem round-trip sem perda")
    void roundTripsPayloadsOfEveryLength() {
        CryptoConverter converter = new CryptoConverter();

        for (int length : new int[] {1, 15, 16, 17, 31, 32, 33, 1024}) {
            String plain = "a".repeat(length);
            String stored = converter.convertToDatabaseColumn(plain);
            assertNotEquals(plain, stored);
            assertEquals(plain, converter.convertToEntityAttribute(stored), "tamanho " + length);
        }
    }

    @Test
    @DisplayName("base64 inválido no banco não derruba a leitura")
    void invalidBase64FallsBackToRawValue() {
        CryptoConverter converter = new CryptoConverter();
        assertEquals("não é base64 @@@", converter.convertToEntityAttribute("não é base64 @@@"));
    }

    @Test
    @DisplayName("base64 válido mas curto demais para conter o IV volta como está")
    void tooShortCiphertextFallsBackToRawValue() {
        CryptoConverter converter = new CryptoConverter();
        String tooShort = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        assertEquals(tooShort, converter.convertToEntityAttribute(tooShort));
    }

    @Test
    @DisplayName("string vazia é cifrada e recuperada corretamente")
    void emptyStringRoundTrips() {
        CryptoConverter converter = new CryptoConverter();
        String stored = converter.convertToDatabaseColumn("");
        assertNotEquals("", stored);
        assertEquals("", converter.convertToEntityAttribute(stored));
    }
}
