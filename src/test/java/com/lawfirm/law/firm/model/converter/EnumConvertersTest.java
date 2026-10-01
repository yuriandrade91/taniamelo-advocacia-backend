package com.lawfirm.law.firm.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.model.AddressType;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.model.DocumentType;
import com.lawfirm.law.firm.model.ExpenseCategory;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.PaymentMethod;
import com.lawfirm.law.firm.model.PaymentStatus;
import com.lawfirm.law.firm.model.Situation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

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
                Arguments.of(new DisabilityGradeConverter(), DisabilityGrade.class),
                Arguments.of(new DocumentTypeConverter(), DocumentType.class),
                // Estava de fora: a varredura do pacote o encontrou. Com o número
                // fixo no lugar dela, o round-trip dele nunca rodou.
                Arguments.of(new ExpenseCategoryConverter(), ExpenseCategory.class),
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

    /**
     * Converters de enum que existem no pacote, por varredura.
     *
     * <p>{@code CryptoConverter} fica de fora porque não é de enum: ele cifra {@code String}, e o
     * contrato verificado aqui (rótulo ↔ constante) não se aplica a ele. Tem teste próprio.
     */
    private static List<Class<?>> convertersNoPacote() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Converter.class));
        List<Class<?>> encontrados = new ArrayList<>();
        for (BeanDefinition definition :
                scanner.findCandidateComponents("com.lawfirm.law.firm.model.converter")) {
            try {
                Class<?> type = Class.forName(definition.getBeanClassName());
                if (type != CryptoConverter.class) encontrados.add(type);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return encontrados;
    }

    @Test
    @DisplayName("a lista coberta contempla todos os converters de enum do pacote")
    void coversEveryConverterInThePackage() {
        // Varredura, e não um número fixo. O teste já se chamava "contempla
        // todos os converters do pacote" mas só comparava `size()` com um
        // literal: um converter novo e não registrado aqui passava assim que
        // alguém subisse o número para fazer o build voltar ao verde — sem
        // nunca rodar o round-trip dele. É o mesmo motivo pelo qual
        // SecuredEndpointsContractTest varre os controllers em vez de listar
        // as rotas de hoje: o defeito que importa é o de amanhã.
        Set<Class<?>> cobertos =
                converters()
                        .map(arguments -> arguments.get()[0].getClass())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> faltando =
                convertersNoPacote().stream()
                        .filter(type -> !cobertos.contains(type))
                        .map(Class::getSimpleName)
                        .sorted()
                        .toList();

        assertTrue(
                faltando.isEmpty(),
                "converter de enum sem round-trip verificado - acrescente em converters(): "
                        + faltando);
        assertEquals(convertersNoPacote().size(), cobertos.size());
    }
}
