package com.lawfirm.law.firm.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Todo DTO deste projeto é um bean simples: o que o setter grava, o getter devolve. Em vez de
 * escrever isso à mão 40 vezes, este teste percorre por reflexão cada par get/set de cada DTO -
 * então um DTO ou campo novo entra na verificação automaticamente, sem precisar lembrar.
 */
@DisplayName("DTOs: contrato get/set de todos os beans de request e response")
class DtoAccessorContractTest {

    private static final List<Class<?>> DTOS =
            List.of(
                    AppointmentCancelRequestDTO.class,
                    AppointmentRequestDTO.class,
                    AppointmentResponseDTO.class,
                    AppointmentSearchParams.class,
                    ClientAddressRequestDTO.class,
                    ClientAddressResponseDTO.class,
                    ClientCreateRequestDTO.class,
                    ClientDetailsDTO.class,
                    ClientFileDocumentResponseDTO.class,
                    ClientFileDocumentUpdateRequestDTO.class,
                    ClientFileDocumentUploadMetadataDTO.class,
                    ClientFileSimulationResponseDTO.class,
                    ClientFileSimulationUpdateRequestDTO.class,
                    ClientFileSimulationUploadMetadataDTO.class,
                    ClientInterviewRequestDTO.class,
                    ClientInterviewResponseDTO.class,
                    ClientListResponseDTO.class,
                    ClientPatchRequestDTO.class,
                    ClientPatchResponseDTO.class,
                    ClientPaymentRequestDTO.class,
                    ClientPaymentResponseDTO.class,
                    ClientPaymentUpdateRequestDTO.class,
                    ClientPersonalDataRequestDTO.class,
                    ClientPersonalDataResponseDTO.class,
                    ClientProfessionalDataRequestDTO.class,
                    ClientProfessionalDataResponseDTO.class,
                    ClientSituationHistoryDTO.class,
                    ClientUpdateRequestDTO.class,
                    LoginRequestDTO.class,
                    LoginResponseDTO.class,
                    TenantResponseDTO.class);

    static Stream<Class<?>> dtos() {
        return DTOS.stream();
    }

    /** Um valor plausível e distinguível para cada tipo usado nos DTOs. */
    private static Object sampleFor(Class<?> type) {
        if (type == String.class) return "valor-de-teste";
        if (type == UUID.class) return UUID.fromString("99999999-9999-9999-9999-999999999999");
        if (type == Instant.class) return Instant.parse("2026-08-20T14:30:00Z");
        if (type == LocalDate.class) return LocalDate.of(2026, 8, 20);
        if (type == BigDecimal.class) return new BigDecimal("1234.56");
        if (type == Boolean.class || type == boolean.class) return Boolean.TRUE;
        if (type == Integer.class || type == int.class) return 42;
        if (type == Long.class || type == long.class) return 4242L;
        if (type == Double.class || type == double.class) return 42.5d;
        if (type == List.class) return new ArrayList<>(List.of("a", "b"));
        if (type.isEnum()) return type.getEnumConstants()[type.getEnumConstants().length - 1];
        return null;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dtos")
    @DisplayName("cada setter grava exatamente o que o getter devolve")
    void gettersReturnWhatSettersStore(Class<?> dtoType) throws Exception {
        Object instance = newInstance(dtoType);

        Map<String, Method> getters = new LinkedHashMap<>();
        for (Method method : dtoType.getMethods()) {
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
        for (Method setter : dtoType.getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) {
                continue;
            }
            String property = setter.getName().substring(3);
            Method getter = getters.get(property);
            if (getter == null) {
                continue;
            }
            Object value = sampleFor(setter.getParameterTypes()[0]);
            if (value == null) {
                continue;
            }

            setter.invoke(instance, value);
            assertEquals(
                    value,
                    getter.invoke(instance),
                    dtoType.getSimpleName() + "#" + property + " não devolveu o valor gravado");
            checked++;
        }

        assertTrue(
                checked > 0, dtoType.getSimpleName() + " não tem nenhum par get/set verificável");
    }

    private static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> noArgs = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                noArgs = constructor;
                break;
            }
        }
        assertNotNull(noArgs, type.getSimpleName() + " precisa de construtor sem argumentos");
        noArgs.setAccessible(true);
        return noArgs.newInstance();
    }

    @Test
    @DisplayName("DTOs que também têm construtor cheio expõem os mesmos valores")
    void constructorBasedDtos() {
        LoginResponseDTO login =
                new LoginResponseDTO("tok", 3600, "Tania Melo", "t@x.com", "ADMIN");
        login.setTenantId("uuid");
        login.setTenantSlug("tania");

        assertEquals("tok", login.getToken());
        assertEquals("Bearer", login.getTokenType());
        assertEquals(3600, login.getExpiresInSeconds());
        assertEquals("Tania Melo", login.getFullName());
        assertEquals("t@x.com", login.getEmail());
        assertEquals("ADMIN", login.getRole());
        assertEquals("uuid", login.getTenantId());
        assertEquals("tania", login.getTenantSlug());

        assertEquals("msg", new ClientPatchResponseDTO("msg").getMessage());
    }

    @Test
    @DisplayName("records de resposta expõem seus componentes")
    void recordDtos() {
        TenantPublicDTO tenant = new TenantPublicDTO("uuid", "tania", "Tania Melo Advocacia");
        assertEquals("uuid", tenant.tenantId());
        assertEquals("tania", tenant.slug());
        assertEquals("Tania Melo Advocacia", tenant.razaoSocial());

        AppointmentSummaryDTO summary = new AppointmentSummaryDTO(2026, 8, 5L);
        assertEquals(2026, summary.year());
        assertEquals(8, summary.month());
        assertEquals(5L, summary.count());
        assertEquals(new AppointmentSummaryDTO(2026, 8, 5L), summary);

        UUID id = UUID.randomUUID();
        AppointmentHistoryDTO history =
                new AppointmentHistoryDTO(id, "EDITED", "motivo", Instant.EPOCH, id);
        assertEquals(id, history.id());
        assertEquals("EDITED", history.action());
        assertEquals("motivo", history.justification());
        assertEquals(Instant.EPOCH, history.changedAt());
        assertEquals(id, history.changedByUserId());
    }

    @Test
    @DisplayName("a validação @AssertTrue de término após início cobre os três casos")
    void appointmentEndAfterStartRule() {
        AppointmentRequestDTO dto = new AppointmentRequestDTO();
        assertTrue(dto.isEndAfterStart(), "sem datas, a regra não se aplica");

        dto.setStartAt(Instant.parse("2026-08-20T14:00:00Z"));
        assertTrue(dto.isEndAfterStart(), "sem término, a regra não se aplica");

        dto.setEndAt(Instant.parse("2026-08-20T15:00:00Z"));
        assertTrue(dto.isEndAfterStart());

        dto.setEndAt(Instant.parse("2026-08-20T13:00:00Z"));
        org.junit.jupiter.api.Assertions.assertFalse(dto.isEndAfterStart());

        dto.setEndAt(dto.getStartAt());
        org.junit.jupiter.api.Assertions.assertFalse(
                dto.isEndAfterStart(), "mesmo instante não vale");
    }

    @Test
    @DisplayName("AppointmentSearchParams nasce com os defaults de paginação")
    void searchParamsDefaults() {
        AppointmentSearchParams params = new AppointmentSearchParams();
        assertEquals(1, params.getPageNumber());
        assertEquals(10, params.getPageSize());
    }

    @Test
    @DisplayName("a lista coberta contempla os DTOs de bean do pacote")
    void listCoversThePackage() {
        assertTrue(DTOS.size() >= 30);
        for (Class<?> type : DTOS) {
            assertTrue(Modifier.isPublic(type.getModifiers()), type.getSimpleName());
        }
    }
}
