package com.lawfirm.law.firm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.Situation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EnumLabelSupport: resolução de enum por nome da constante ou label PT-BR")
class EnumLabelSupportTest {

    @Test
    @DisplayName("null e string vazia devolvem null")
    void nullAndEmptyReturnNull() {
        assertNull(EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, null));
        assertNull(EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, ""));
        assertNull(EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, "   "));
        assertNull(EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, "\"\""));
    }

    @ParameterizedTest(name = "\"{0}\" resolve para APOSENTADORIA_RURAL")
    @ValueSource(
            strings = {
                "APOSENTADORIA_RURAL",
                "aposentadoria_rural",
                "Aposentadoria rural",
                "APOSENTADORIA RURAL",
                "aposentadoria  rural",
                "\"Aposentadoria rural\"",
                "  Aposentadoria rural  "
            })
    void resolvesByNameOrLabelIgnoringCaseAndPunctuation(String raw) {
        assertEquals(
                BenefitType.APOSENTADORIA_RURAL,
                EnumLabelSupport.fromLabel(BenefitType.class, BenefitType::getLabel, raw));
    }

    @ParameterizedTest(name = "\"{0}\" resolve para PLANEJAMENTO_CONCLUIDO (sem acento)")
    @ValueSource(
            strings = {
                "Planejamento concluído",
                "Planejamento concluido",
                "PLANEJAMENTO CONCLUIDO",
                "planejamento-concluido"
            })
    void resolvesIgnoringDiacritics(String raw) {
        assertEquals(
                Situation.PLANEJAMENTO_CONCLUIDO,
                EnumLabelSupport.fromLabel(Situation.class, Situation::getLabel, raw));
    }

    @Test
    @DisplayName("valor desconhecido lança IllegalArgumentException listando os aceitos")
    void unknownValueThrowsWithAcceptedValues() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                EnumLabelSupport.fromLabel(
                                        Gender.class, Gender::getLabel, "não-existe"));
        assertTrue(ex.getMessage().contains("não-existe"));
        assertTrue(ex.getMessage().contains("Masculino"));
        assertTrue(ex.getMessage().contains("Feminino"));
    }

    @Test
    @DisplayName("aspas envolvendo o valor são removidas antes da comparação exata")
    void stripsSurroundingQuotes() {
        assertEquals(
                Gender.MASCULINO,
                EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, "\"Masculino\""));
    }

    @Test
    @DisplayName("aspa solta ainda resolve, porque a normalização descarta pontuação")
    void unbalancedQuoteStillResolvesViaNormalization() {
        assertEquals(
                Gender.MASCULINO,
                EnumLabelSupport.fromLabel(Gender.class, Gender::getLabel, "\"Masculino"));
    }

    @Test
    @DisplayName("normalize remove acentos, pontuação e caixa")
    void normalizeStripsEverythingButAlphanumerics() {
        assertEquals("aposentadoriarural", EnumLabelSupport.normalize("Aposentadoria Rural"));
        assertEquals("naobinario", EnumLabelSupport.normalize("Não-binário"));
        assertEquals("", EnumLabelSupport.normalize(null));
        assertEquals("", EnumLabelSupport.normalize("---"));
    }
}
