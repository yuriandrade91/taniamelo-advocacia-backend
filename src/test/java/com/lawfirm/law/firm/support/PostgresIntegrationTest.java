package com.lawfirm.law.firm.support;

import com.lawfirm.law.firm.audit.AuditLogListener;
import com.lawfirm.law.firm.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

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
public abstract class PostgresIntegrationTest {

    @Autowired private AuditLogListener auditLogListener;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    /**
     * Religa a auditoria a ESTE contexto antes de cada teste.
     *
     * <p>{@link AuditLogListener} guarda repositório e transaction manager em campos estáticos,
     * preenchidos pelo {@code init()} quando um contexto sobe - e vale o último que subiu. Em
     * produção há um contexto só. Na suíte há mais de um: {@code MassaDemonstracaoSeedTest} cria o
     * seu, com outro container. Se ele sobe depois do contexto principal, os testes que voltam a
     * usar o principal (do cache) passam a gravar auditoria no banco do outro, e quem confere a
     * trilha não acha nada. Como a ordem das classes depende do sistema de arquivos, isso passava
     * no macOS e quebrava no Linux do CI.
     */
    @BeforeEach
    void rebindAuditLogListenerToThisContext() {
        auditLogListener.init(auditLogRepository, transactionManager);
    }
}
