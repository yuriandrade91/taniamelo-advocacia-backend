package com.lawfirm.law.firm.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CryptoConverter: AES-256-GCM em repouso para campos sensíveis")
class CryptoConverterTest {

    private CryptoConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CryptoConverter();
    }

    @Test
    @DisplayName("null passa direto nas duas direções")
    void nullPassesThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @ParameterizedTest(name = "round-trip de \"{0}\"")
    @ValueSource(
            strings = {
                "senha123",
                "",
                "acentuação e ç",
                "uma senha bem comprida com espaços e símbolos !@#$%^&*()",
                "🔐 emoji"
            })
    void encryptsAndDecryptsBackToTheOriginal(String plain) {
        String stored = converter.convertToDatabaseColumn(plain);
        assertNotEquals(plain, stored);
        assertEquals(plain, converter.convertToEntityAttribute(stored));
    }

    @Test
    @DisplayName("o valor cifrado é base64 e nunca contém o texto original")
    void ciphertextIsOpaqueBase64() {
        String stored = converter.convertToDatabaseColumn("senha-do-inss");
        assertTrue(stored.matches("^[A-Za-z0-9+/]+={0,2}$"), "esperava base64: " + stored);
        assertTrue(Base64.getDecoder().decode(stored).length > 12, "IV + ciphertext");
        assertEquals(-1, stored.indexOf("senha-do-inss"));
    }

    @Test
    @DisplayName("IV aleatório por valor: cifrar duas vezes o mesmo texto dá saídas diferentes")
    void usesARandomIvPerValue() {
        String first = converter.convertToDatabaseColumn("mesma-senha");
        String second = converter.convertToDatabaseColumn("mesma-senha");
        assertNotEquals(first, second);
        assertEquals("mesma-senha", converter.convertToEntityAttribute(first));
        assertEquals("mesma-senha", converter.convertToEntityAttribute(second));
    }

    @Test
    @DisplayName("valor legado em claro (gravado antes da criptografia) volta como está")
    void legacyPlaintextIsReturnedAsIs() {
        assertEquals(
                "senha-antiga-em-claro",
                converter.convertToEntityAttribute("senha-antiga-em-claro"));
    }

    @Test
    @DisplayName(
            "valor corrompido ou cifrado com outra chave volta como está, sem derrubar a leitura")
    void corruptedValueFallsBackToRawValue() {
        String stored = converter.convertToDatabaseColumn("original");
        String corrupted = stored.substring(0, stored.length() - 4) + "AAAA";
        assertEquals(corrupted, converter.convertToEntityAttribute(corrupted));
    }

    @Test
    @DisplayName("cada instância do converter lê o que a outra escreveu (mesma chave)")
    void instancesAreInterchangeable() {
        String stored = new CryptoConverter().convertToDatabaseColumn("compartilhado");
        assertEquals("compartilhado", new CryptoConverter().convertToEntityAttribute(stored));
    }
}
