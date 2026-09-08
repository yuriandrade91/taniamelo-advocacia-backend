package com.lawfirm.law.firm.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.lawfirm.law.firm.model.AddressType;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.DocumentType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.model.Situation;
import jakarta.persistence.AttributeConverter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Todos os AttributeConverter de enum seguem a mesma regra: grava o label, lê pelo label e devolve
 * null (em vez de estourar) quando o banco tem um valor legado desconhecido.
 */
@DisplayName("AttributeConverters de enum: label <-> constante")
class EnumConvertersTest {

    static Stream<Arguments> converters() {
        return Stream.of(
                Arguments.of(new AddressTypeConverter(), AddressType.class),
                Arguments.of(new AppointmentModalityConverter(), AppointmentModality.class),
                Arguments.of(new AppointmentStatusConverter(), AppointmentStatus.class),
                Arguments.of(new AppointmentTypeConverter(), AppointmentType.class),
                Arguments.of(new BenefitTypeConverter(), BenefitType.class),
                Arguments.of(new ClientTypeConverter(), ClientType.class),
                Arguments.of(new DocumentTypeConverter(), DocumentType.class),
                Arguments.of(new GenderConverter(), Gender.class),
                Arguments.of(new MaritalStatusConverter(), MaritalStatus.class),
                Arguments.of(new PaymentMethodConverter(), PaymentMethod.class),
                Arguments.of(new PaymentStatusConverter(), PaymentStatus.class),
                Arguments.of(new SituationConverter(), Situation.class));
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{1}")
    @MethodSource("converters")
    @DisplayName("round-trip de todas as constantes")
    void roundTripsEveryConstant(
            AttributeConverter<Object, String> converter, Class<? extends Enum<?>> enumType)
            throws Exception {
        Method getLabel = enumType.getMethod("getLabel");
        for (Object constant : enumType.getEnumConstants()) {
            String label = (String) getLabel.invoke(constant);
            assertEquals(label, converter.convertToDatabaseColumn(constant));
            assertSame(constant, converter.convertToEntityAttribute(label));
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("converters")
    @DisplayName("null passa direto nas duas direções")
    void nullPassesThrough(
            AttributeConverter<Object, String> converter, Class<? extends Enum<?>> enumType) {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("converters")
    @DisplayName("valor legado desconhecido no banco vira null em vez de derrubar a leitura")
    void unknownDatabaseValueBecomesNull(
            AttributeConverter<Object, String> converter, Class<? extends Enum<?>> enumType) {
        assertNull(converter.convertToEntityAttribute("valor-legado-que-nao-existe"));
    }

    @Test
    @DisplayName("leitura aceita variações de caixa/acento gravadas por versões antigas")
    void readingToleratesCaseAndAccentVariations() {
        assertSame(
                Situation.PLANEJAMENTO_CONCLUIDO,
                new SituationConverter().convertToEntityAttribute("planejamento concluido"));
        assertSame(
                BenefitType.APOSENTADORIA_RURAL,
                new BenefitTypeConverter().convertToEntityAttribute("APOSENTADORIA_RURAL"));
    }

    @Test
    @DisplayName("a lista coberta contempla todos os converters de enum do pacote")
    void coversEveryConverterInThePackage() {
        List<Arguments> covered = converters().toList();
        assertEquals(12, covered.size());
    }
}
