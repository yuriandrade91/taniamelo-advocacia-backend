package com.lawfirm.law.firm.support;

import com.lawfirm.law.firm.model.Appointment;
import com.lawfirm.law.firm.model.AppointmentModality;
import com.lawfirm.law.firm.model.AppointmentStatus;
import com.lawfirm.law.firm.model.AppointmentType;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.model.User;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fábricas de entidades para os testes unitários (nenhuma toca o banco). */
public final class TestFixtures {

    public static final UUID CLIENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private TestFixtures() {}

    /** Entidades com id gerado pelo banco não expõem setId - este helper injeta via reflexão. */
    public static <T> T withId(T entity, UUID id) {
        setField(entity, "id", id);
        return entity;
    }

    public static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Não foi possível setar " + name, e);
            }
        }
        throw new IllegalStateException("Campo inexistente: " + name);
    }

    public static Client client() {
        return client(CLIENT_ID, "Maria da Silva");
    }

    public static Client client(UUID id, String fullName) {
        Client client = new Client();
        client.setId(id);
        client.setFullName(fullName);
        client.setBirthDate(LocalDate.of(1980, 5, 20));
        client.setCpf("529.982.247-25");
        client.setMotherName("Joana da Silva");
        client.setMobilePhone("+5511999999999");
        client.setInssPassword("senha-inss");
        client.setGender(Gender.FEMININO);
        client.setMaritalStatus(MaritalStatus.CASADO);
        client.setClientType(ClientType.POTENCIAL);
        client.setSituation(Situation.FORMULARIO_PREENCHIDO);
        client.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        client.setNotBillable(false);
        client.setCreatedAt(Instant.parse("2026-01-10T10:00:00Z"));
        client.setUpdatedAt(Instant.parse("2026-02-10T10:00:00Z"));
        return client;
    }

    public static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setFullName("Tania Melo");
        user.setEmail("dra.tania@taniamelo.adv.br");
        user.setUsername("dra.tania");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return user;
    }

    public static Tenant tenant() {
        Tenant tenant = new Tenant();
        withId(tenant, TENANT_ID);
        tenant.setSchemaName("tenant_tania");
        tenant.setSlug("tania");
        tenant.setRazaoSocial("Tania Melo Advocacia");
        tenant.setCnpj("12.345.678/0001-90");
        tenant.setResponsavel("Tania Melo");
        tenant.setEmail("contato@taniamelo.adv.br");
        tenant.setTelefone("+551133334444");
        tenant.setPlano("pro");
        tenant.setStatus("ativo");
        return tenant;
    }

    public static Appointment appointment() {
        Appointment appointment = new Appointment();
        withId(appointment, UUID.fromString("44444444-4444-4444-4444-444444444444"));
        appointment.setTitle("Entrevista inicial");
        appointment.setType(AppointmentType.ENTREVISTA);
        appointment.setStartAt(Instant.parse("2099-08-20T14:30:00Z"));
        appointment.setEndAt(Instant.parse("2099-08-20T15:30:00Z"));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.AGENDADO);
        return appointment;
    }
}
