package com.lawfirm.law.firm.audit;

import com.lawfirm.law.firm.security.CurrentUser;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Listener JPA plugado via {@code @EntityListeners(AuditLogListener.class)} em toda entidade {@link
 * Auditable}: grava automaticamente, em audit_log, quem criou/alterou/removeu cada linha - sem
 * nenhum service precisar lembrar de chamar isso manualmente (é exatamente esse "esquecimento" que
 * causava os gaps encontrados antes desta mudança: exclusões físicas - endereço, cliente - não
 * deixavam nenhum rastro de quem executou a ação).
 *
 * <p>Entity listeners são instanciados pelo provider JPA (Hibernate), não pelo Spring, então não dá
 * para injetar as dependências no construtor normalmente. Solução padrão: um único bean Spring
 * desta classe injeta o repositório e o transaction manager em campos estáticos logo na subida do
 * contexto; o próprio Hibernate também instancia sua própria cópia (sem nada setado) para registrar
 * como listener - as duas cópias compartilham os mesmos campos estáticos, então funciona.
 *
 * <p>A gravação NÃO acontece de forma síncrona dentro do callback: salvar uma entidade nova
 * (AuditLog) durante {@code @PostPersist}/{@code @PostUpdate}/{@code @PostRemove} força um flush
 * aninhado que reentra no {@code ActionQueue} do Hibernate ainda em processamento, e derruba a
 * transação inteira com {@code ConcurrentModificationException} - confirmado ao vivo com um teste
 * de integração real (salvar um Client já quebrava). Por isso o registro é adiado para depois do
 * commit da transação principal (via {@link TransactionSynchronization#afterCommit()}), numa
 * transação nova e independente - nunca compartilha sessão/Hibernate com a operação que está sendo
 * auditada.
 *
 * <p>Limitação conhecida: isto só dispara para remoções que passam pelo EntityManager ({@code
 * repository.delete(...)}/{@code deleteById(...)}). Uma remoção em cascata feita pelo Postgres via
 * {@code ON DELETE CASCADE} (ex.: apagar um cliente remove endereços/arquivos/ pagamentos em
 * cascata no banco) não passa pelo Hibernate e por isso não gera uma linha própria por registro
 * filho - só a remoção do cliente em si é auditada. Ver nota em docs/ROADMAP.md.
 */
@Component
public class AuditLogListener {

    private static volatile AuditLogRepository repository;
    private static volatile PlatformTransactionManager transactionManager;

    @Autowired
    public void init(AuditLogRepository repository, PlatformTransactionManager transactionManager) {
        AuditLogListener.repository = repository;
        AuditLogListener.transactionManager = transactionManager;
    }

    @PostPersist
    public void onCreate(Object entity) {
        record(entity, AuditAction.CREATE);
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        record(entity, AuditAction.UPDATE);
    }

    @PostRemove
    public void onRemove(Object entity) {
        record(entity, AuditAction.DELETE);
    }

    private void record(Object entity, AuditAction action) {
        AuditLogRepository repo = repository;
        PlatformTransactionManager txManager = transactionManager;
        if (repo == null || txManager == null || !(entity instanceof Auditable auditable)) {
            // Dependências ainda não injetadas (ex.: entidade carregada antes do contexto Spring
            // terminar de subir) - não derruba a transação principal por causa da auditoria.
            return;
        }

        String entityName = entity.getClass().getSimpleName();
        UUID entityId = auditable.getId();
        UUID performedBy = CurrentUser.id();

        Runnable persistAuditRow =
                () -> {
                    TransactionTemplate newTransaction = new TransactionTemplate(txManager);
                    newTransaction.setPropagationBehavior(
                            TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    newTransaction.executeWithoutResult(
                            status ->
                                    repo.save(
                                            new AuditLog(
                                                    entityName,
                                                    entityId,
                                                    action,
                                                    performedBy,
                                                    null)));
                };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            persistAuditRow.run();
                        }
                    });
        } else {
            // Sem transação ativa (ex.: chamada fora de um @Transactional) - grava direto, melhor
            // esforço.
            persistAuditRow.run();
        }
    }
}
