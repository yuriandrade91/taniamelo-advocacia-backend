package com.lawfirm.law.firm.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.support.TestFixtures;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditLogListener: trilha automática de CREATE/UPDATE/DELETE")
class AuditLogListenerUnitTest {

    @Mock private AuditLogRepository repository;
    @Mock private PlatformTransactionManager transactionManager;

    private AuditLogListener listener;

    // AuditLogListener guarda suas dependências em campos ESTÁTICOS (o provider JPA instancia o
    // listener, não o Spring - ver o javadoc da classe). Isso é estado global do JVM: se este teste
    // deixasse mocks - ou null - ali, os testes de integração que reusam o contexto Spring em cache
    // parariam de gravar auditoria, porque o @Autowired init() não roda de novo num contexto já
    // criado. Por isso salvamos o valor original antes e restauramos depois de cada teste.
    private Object originalRepository;
    private Object originalTransactionManager;

    @BeforeEach
    void setUp() {
        originalRepository = readStatic("repository");
        originalTransactionManager = readStatic("transactionManager");

        listener = new AuditLogListener();
        listener.init(repository, transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        writeStatic("repository", originalRepository);
        writeStatic("transactionManager", originalTransactionManager);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Object readStatic(String name) {
        return withField(
                name,
                field -> {
                    try {
                        return field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private static void writeStatic(String name, Object value) {
        withField(
                name,
                field -> {
                    try {
                        field.set(null, value);
                        return null;
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private static Object withField(
            String name, java.util.function.Function<Field, Object> action) {
        try {
            Field field = AuditLogListener.class.getDeclaredField(name);
            field.setAccessible(true);
            return action.apply(field);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Campo estático inexistente: " + name, e);
        }
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("sem transação ativa, grava direto (melhor esforço)")
    void writesImmediatelyWithoutActiveTransaction() {
        Client client = TestFixtures.client();

        listener.onCreate(client);

        AuditLog log = captureSaved();
        assertEquals("Client", log.getEntityName());
        assertEquals(client.getId(), log.getEntityId());
        assertEquals(AuditAction.CREATE, log.getAction());
    }

    @Test
    @DisplayName("cada callback registra a ação correspondente")
    void mapsEachCallbackToItsAction() {
        Client client = TestFixtures.client();

        listener.onCreate(client);
        listener.onUpdate(client);
        listener.onRemove(client);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertEquals(AuditAction.CREATE, saved.getAllValues().get(0).getAction());
        assertEquals(AuditAction.UPDATE, saved.getAllValues().get(1).getAction());
        assertEquals(AuditAction.DELETE, saved.getAllValues().get(2).getAction());
    }

    @Test
    @DisplayName("registra o usuário autenticado como autor da ação")
    void recordsTheAuthenticatedUser() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));

        listener.onUpdate(TestFixtures.client());

        assertEquals(TestFixtures.USER_ID, captureSaved().getPerformedBy());
    }

    @Test
    @DisplayName("sem usuário autenticado grava performedBy nulo (job de sistema)")
    void anonymousActionsHaveNoPerformer() {
        SecurityContextHolder.clearContext();

        listener.onCreate(TestFixtures.client());

        assertNull(captureSaved().getPerformedBy());
    }

    @Test
    @DisplayName("com transação ativa, adia a gravação para depois do commit")
    void defersWriteUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            listener.onCreate(TestFixtures.client());

            verify(repository, never()).save(any());
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());

            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
            assertNotNull(captureSaved());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("entidade que não é Auditable é ignorada")
    void ignoresNonAuditableEntities() {
        listener.onCreate("uma string qualquer");
        listener.onUpdate(new Object());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("sem dependências injetadas, não derruba a transação principal")
    void withoutDependenciesItIsANoOp() {
        // O @AfterEach restaura o estado estático original, então zerar aqui não vaza
        // para os testes de integração que dependem do listener funcionando.
        listener.init(null, null);

        listener.onCreate(TestFixtures.client());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("o estado estático do listener é restaurado ao fim de cada teste")
    void staticStateIsRestoredAfterEachTest() {
        // Guarda: se esta classe deixar mocks ou null nos campos estáticos, o
        // AuditLogListenerTest (integração, contexto Spring em cache) passa a gravar zero
        // linhas de auditoria - e falha de forma dependente da ordem de execução.
        assertSame(repository, readStatic("repository"), "durante o teste, valem os mocks");
        assertSame(transactionManager, readStatic("transactionManager"));
        assertNotNull(listener);
    }

    @Test
    @DisplayName("AuditLog guarda o snapshot recebido")
    void auditLogKeepsItsData() {
        UUID entityId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();

        AuditLog log = new AuditLog("Client", entityId, AuditAction.DELETE, performedBy, "detalhe");

        assertEquals("Client", log.getEntityName());
        assertEquals(entityId, log.getEntityId());
        assertEquals(AuditAction.DELETE, log.getAction());
        assertEquals(performedBy, log.getPerformedBy());
        assertEquals("detalhe", log.getDetail());
    }

    @Test
    @DisplayName("o transaction status devolvido pelo manager é o esperado pelo template")
    void transactionManagerContract() {
        TransactionStatus status = transactionManager.getTransaction(null);
        assertNotNull(status);
    }
}
