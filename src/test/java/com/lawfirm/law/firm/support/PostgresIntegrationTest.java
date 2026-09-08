package com.lawfirm.law.firm.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base das classes de teste que precisam do contexto completo do Spring e de um banco de verdade.
 *
 * <p>Existe para que a tríade {@code @SpringBootTest} + perfil {@code test} + container Postgres
 * seja declarada em um lugar só. Repetir as três anotações em cada classe não é só verboso: basta
 * uma divergir para o Spring criar um segundo contexto e, com ele, um segundo container.
 *
 * <p>Herde daqui em qualquer {@code *IntegrationTest} ou {@code *E2ETest}. Testes de unidade
 * (Mockito puro, {@code @WebMvcTest}) não devem herdar - eles não precisam de banco e ficariam
 * lentos à toa.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
public abstract class PostgresIntegrationTest {}
