package com.lawfirm.law.firm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.audit.Auditable;
import com.lawfirm.law.firm.support.TestFixtures;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Entidades JPA: contrato get/set e os callbacks de ciclo de vida (@PrePersist/@PreUpdate) que
 * populam createdAt/updatedAt - a parte que o resto do sistema assume que sempre existe.
 */
@DisplayName("Entidades JPA: acessores e callbacks de ciclo de vida")
class EntityContractTest {

    private static final List<Class<?>> ENTITIES =
            List.of(
                    Appointment.class,
                    AppointmentHistory.class,
                    Client.class,
                    ClientAddress.class,
                    ClientFile.class,
                    ClientInterview.class,
                    ClientPayment.class,
                    ClientSituationHistory.class,
                    RefreshToken.class,
                    Tenant.class,
                    User.class);

    static Stream<Class<?>> entities() {
        return ENTITIES.stream();
    }

    private static Object sampleFor(Class<?> type) {
        if (type == String.class) return "valor-de-teste";
        if (type == UUID.class) return UUID.fromString("88888888-8888-8888-8888-888888888888");
        if (type == Instant.class) return Instant.parse("2026-08-20T14:30:00Z");
        if (type == LocalDate.class) return LocalDate.of(2026, 8, 20);
        if (type == BigDecimal.class) return new BigDecimal("99.90");
        if (type == Boolean.class || type == boolean.class) return Boolean.TRUE;
        if (type == Integer.class || type == int.class) return 7;
        if (type == Long.class || type == long.class) return 77L;
        if (type == Client.class) return TestFixtures.client();
        if (type == Appointment.class) return TestFixtures.appointment();
        if (type.isEnum()) return type.getEnumConstants()[0];
        return null;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    @DisplayName("cada setter grava exatamente o que o getter devolve")
    void gettersReturnWhatSettersStore(Class<?> entityType) throws Exception {
        Object instance = entityType.getDeclaredConstructor().newInstance();

        Map<String, Method> getters = new LinkedHashMap<>();
        for (Method method : entityType.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                getters.put(name.substring(3), method);
            } else if (name.startsWith("is") && name.length() > 2) {
                getters.put(name.substring(2), method);
            }
        }

        int checked = 0;
        for (Method setter : entityType.getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) {
                continue;
            }
            Method getter = getters.get(setter.getName().substring(3));
            Object value = sampleFor(setter.getParameterTypes()[0]);
            if (getter == null || value == null) {
                continue;
            }
            setter.invoke(instance, value);
            assertEquals(
                    value,
                    getter.invoke(instance),
                    entityType.getSimpleName() + "#" + setter.getName());
            checked++;
        }

        assertTrue(checked > 0, entityType.getSimpleName() + " não tem par get/set verificável");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    @DisplayName("os callbacks @PrePersist/@PreUpdate populam os carimbos de tempo")
    void lifecycleCallbacksStampTimestamps(Class<?> entityType) throws Exception {
        Object instance = entityType.getDeclaredConstructor().newInstance();

        for (Method method : entityType.getDeclaredMethods()) {
            boolean isCallback =
                    method.isAnnotationPresent(jakarta.persistence.PrePersist.class)
                            || method.isAnnotationPresent(jakarta.persistence.PreUpdate.class);
            if (!isCallback) {
                continue;
            }
            method.setAccessible(true);
            method.invoke(instance);
        }

        for (String stamp :
                new String[] {
                    "getCreatedAt",
                    "getUpdatedAt",
                    "getUploadedAt",
                    "getPerformedAt",
                    "getChangedAt"
                }) {
            try {
                Method getter = entityType.getMethod(stamp);
                Object value = getter.invoke(instance);
                if (hasCallback(entityType)) {
                    assertNotNull(
                            value,
                            entityType.getSimpleName() + "#" + stamp + " deveria ser preenchido");
                }
            } catch (NoSuchMethodException ignored) {
                // a entidade não tem esse carimbo
            }
        }
    }

    private static boolean hasCallback(Class<?> entityType) {
        for (Method method : entityType.getDeclaredMethods()) {
            if (method.isAnnotationPresent(jakarta.persistence.PrePersist.class)) {
                return true;
            }
        }
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entities")
    @DisplayName("carimbos já preenchidos são preservados pelo @PrePersist (não sobrescreve)")
    void lifecycleCallbacksDoNotOverwriteExistingTimestamps(Class<?> entityType) throws Exception {
        Object instance = entityType.getDeclaredConstructor().newInstance();
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");

        boolean anyStampSet = false;
        for (String property : new String[] {"CreatedAt", "UpdatedAt", "UploadedAt", "ChangedAt"}) {
            try {
                entityType.getMethod("set" + property, Instant.class).invoke(instance, fixed);
                anyStampSet = true;
            } catch (NoSuchMethodException ignored) {
                // a entidade não tem esse carimbo
            }
        }
        if (!anyStampSet) {
            return;
        }

        for (Method method : entityType.getDeclaredMethods()) {
            if (method.isAnnotationPresent(jakarta.persistence.PrePersist.class)) {
                method.setAccessible(true);
                method.invoke(instance);
            }
        }

        for (String property : new String[] {"CreatedAt", "UpdatedAt", "UploadedAt", "ChangedAt"}) {
            try {
                Method getter = entityType.getMethod("get" + property);
                assertEquals(
                        fixed,
                        getter.invoke(instance),
                        entityType.getSimpleName() + "#" + property + " foi sobrescrito");
            } catch (NoSuchMethodException ignored) {
                // a entidade não tem esse carimbo
            }
        }
    }

    @Test
    @DisplayName("AuditLog é imutável: só construtor e getters, sem setters")
    void auditLogIsImmutable() {
        for (Method method : com.lawfirm.law.firm.audit.AuditLog.class.getMethods()) {
            assertTrue(
                    !method.getName().startsWith("set"),
                    "AuditLog não deveria ter setter: " + method.getName());
        }

        UUID entityId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();
        com.lawfirm.law.firm.audit.AuditLog log =
                new com.lawfirm.law.firm.audit.AuditLog(
                        "Client",
                        entityId,
                        com.lawfirm.law.firm.audit.AuditAction.UPDATE,
                        performedBy,
                        "detalhe");

        assertEquals("Client", log.getEntityName());
        assertEquals(entityId, log.getEntityId());
        assertEquals(com.lawfirm.law.firm.audit.AuditAction.UPDATE, log.getAction());
        assertEquals(performedBy, log.getPerformedBy());
        assertEquals("detalhe", log.getDetail());
        assertNull(log.getId(), "id só existe após a persistência");
    }

    @Test
    @DisplayName("as entidades auditáveis expõem o id pelo contrato Auditable")
    void auditableEntitiesExposeTheirId() {
        Client client = TestFixtures.client();
        assertTrue(client instanceof Auditable);
        assertEquals(TestFixtures.CLIENT_ID, ((Auditable) client).getId());
    }

    @Test
    @DisplayName("RefreshToken#isActive combina expiração e revogação")
    void refreshTokenActivity() {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        assertTrue(token.isActive());

        token.setRevokedAt(Instant.now());
        org.junit.jupiter.api.Assertions.assertFalse(token.isActive());

        RefreshToken expired = new RefreshToken();
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        org.junit.jupiter.api.Assertions.assertFalse(expired.isActive());
    }

    @Test
    @DisplayName("uma entidade recém-criada não tem id até ser persistida")
    void newEntityHasNoId() {
        assertNull(new ClientAddress().getId());
        assertNull(new ClientPayment().getId());
        assertNull(new ClientInterview().getId());
        assertNull(new ClientFile().getId());
    }
}
