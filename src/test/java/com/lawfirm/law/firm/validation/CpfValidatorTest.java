package com.lawfirm.law.firm.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CpfValidator: dígitos verificadores do CPF")
class CpfValidatorTest {

    private CpfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CpfValidator();
        validator.initialize(null);
    }

    @ParameterizedTest(name = "\"{0}\" é um CPF válido")
    @ValueSource(
            strings = {
                "529.982.247-25",
                "52998224725",
                "111.444.777-35",
                "11144477735",
                "100.000.037-00",
                "529 982 247 25"
            })
    void acceptsValidCpf(String cpf) {
        assertTrue(validator.isValid(cpf, null));
    }

    @ParameterizedTest(name = "\"{0}\" é rejeitado")
    @ValueSource(
            strings = {
                "529.982.247-26",
                "111.444.777-30",
                "123.456.789-00",
                "000.000.000-00",
                "111.111.111-11",
                "999.999.999-99",
                "1234567890",
                "123456789012",
                "",
                "abc.def.ghi-jk"
            })
    void rejectsInvalidCpf(String cpf) {
        assertFalse(validator.isValid(cpf, null));
    }

    @Test
    @DisplayName("null é rejeitado (a obrigatoriedade fica com @NotBlank)")
    void rejectsNull() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    @DisplayName("sequências repetidas são rejeitadas mesmo com dígitos coerentes")
    void rejectsRepeatedSequences() {
        for (int d = 0; d <= 9; d++) {
            String repeated = String.valueOf(d).repeat(11);
            assertFalse(validator.isValid(repeated, null), "deveria rejeitar " + repeated);
        }
    }

    @Test
    @DisplayName("CPF cujo dígito verificador calculado é 0 via resto 10/11")
    void acceptsCpfWithZeroCheckDigits() {
        assertTrue(validator.isValid("100.000.037-00", null));
        assertTrue(validator.isValid("10000004600", null));
    }
}
