package com.lawfirm.law.firm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lawfirm.law.firm.audit.AuditAction;
import com.lawfirm.law.firm.util.EnumLabelSupport;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Todo enum de domínio segue o mesmo contrato: getLabel() como @JsonValue, fromLabel()
 * como @JsonCreator (aceitando nome da constante OU label) e toString() == label. Este teste
 * percorre todas as constantes de todos eles, então uma constante nova entra na cobertura
 * automaticamente.
 */
@DisplayName("Enums de domínio: contrato label/fromLabel/toString")
class DomainEnumsTest {

    private static final List<Class<? extends Enum<?>>> LABELLED_ENUMS =
            List.of(
                    Gender.class,
                    MaritalStatus.class,
                    Situation.class,
                    BenefitType.class,
                    ClientType.class,
                    AddressType.class,
                    DocumentType.class,
                    PaymentMethod.class,
                    PaymentStatus.class,
                    AppointmentType.class,
                    AppointmentStatus.class,
                    AppointmentModality.class);

    static Stream<Class<? extends Enum<?>>> labelledEnums() {
        return LABELLED_ENUMS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("labelledEnums")
    @DisplayName("cada constante resolve pelo nome, pelo label e sem acento")
    void everyConstantRoundTrips(Class<? extends Enum<?>> type) throws Exception {
        Method getLabel = type.getMethod("getLabel");
        Method fromLabel = type.getMethod("fromLabel", String.class);

        for (Object constant : type.getEnumConstants()) {
            Enum<?> value = (Enum<?>) constant;
            String label = (String) getLabel.invoke(value);

            assertEquals(label, value.toString(), type.getSimpleName() + "." + value.name());
            assertSame(value, fromLabel.invoke(null, value.name()));
            assertSame(value, fromLabel.invoke(null, label));
            assertSame(value, fromLabel.invoke(null, label.toUpperCase()));
            assertSame(value, fromLabel.invoke(null, EnumLabelSupport.normalize(label)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("labelledEnums")
    @DisplayName("null vira null e valor desconhecido estoura")
    void nullAndUnknownValues(Class<? extends Enum<?>> type) throws Exception {
        Method fromLabel = type.getMethod("fromLabel", String.class);
        assertNull(fromLabel.invoke(null, (Object) null));

        Exception ex =
                assertThrows(
                        Exception.class, () -> fromLabel.invoke(null, "valor-que-nao-existe-42"));
        assertSame(IllegalArgumentException.class, ex.getCause().getClass());
    }

    @Test
    @DisplayName("FileKind e AuditAction são enums puramente técnicos (sem label)")
    void technicalEnums() {
        assertEquals(2, FileKind.values().length);
        assertSame(FileKind.DOCUMENT, FileKind.valueOf("DOCUMENT"));
        assertSame(FileKind.SIMULATION, FileKind.valueOf("SIMULATION"));

        assertEquals(3, AuditAction.values().length);
        assertSame(AuditAction.CREATE, AuditAction.valueOf("CREATE"));
        assertSame(AuditAction.UPDATE, AuditAction.valueOf("UPDATE"));
        assertSame(AuditAction.DELETE, AuditAction.valueOf("DELETE"));
    }

    @Test
    @DisplayName("Role e AppointmentAction cobrem os papéis/ações esperados")
    void roleAndAppointmentAction() {
        assertSame(Role.ADMIN, Role.valueOf("ADMIN"));
        for (Role role : Role.values()) {
            assertSame(role, Role.valueOf(role.name()));
        }
        for (AppointmentAction action : AppointmentAction.values()) {
            assertSame(action, AppointmentAction.valueOf(action.name()));
        }
    }

    @Test
    @DisplayName("labels específicos usados pelo frontend não mudam sem querer")
    void labelsAreStable() {
        assertEquals("Masculino", Gender.MASCULINO.getLabel());
        assertEquals("Solteiro(a)", MaritalStatus.SOLTEIRO.getLabel());
        assertEquals("Formulário preenchido", Situation.FORMULARIO_PREENCHIDO.getLabel());
        assertEquals("Aposentadoria por idade", BenefitType.APOSENTADORIA_POR_IDADE.getLabel());
        assertEquals("Potencial", ClientType.POTENCIAL.getLabel());
        assertEquals("Pendente", PaymentStatus.PENDENTE.getLabel());
        assertEquals("Agendado", AppointmentStatus.AGENDADO.getLabel());
    }
}
