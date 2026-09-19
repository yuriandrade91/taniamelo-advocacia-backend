package com.lawfirm.law.firm.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.service.ClientService;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A gravação da auditoria acontece em afterCommit (ver {@link AuditLogListener}), então estes
 * testes não podem rodar dentro de uma transação de teste que sempre faz rollback (afterCommit
 * nunca dispararia) - cada teste commita de verdade e limpa os próprios dados em @AfterEach.
 */
class AuditLogListenerTest extends PostgresIntegrationTest {

    @Autowired private ClientRepository clientRepository;

    @Autowired private AuditLogRepository auditLogRepository;

    @Autowired private ClientService clientService;

    private UUID createdClientId;

    @AfterEach
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void cleanup() {
        if (createdClientId != null) {
            clientRepository.deleteById(createdClientId);
        }
    }

    @Test
    void savingAClientRecordsAnAuditLogEntryAfterCommit() {
        Client client = new Client();
        client.setFullName("Auditoria Teste");
        client.setBirthDate(LocalDate.of(1990, 1, 1));
        client.setCpf("000.000.001-91");
        client.setMotherName("Mãe Teste");
        client.setMobilePhone("+5511900000000");
        client.setInssPassword("senha123");
        client.setGender(Gender.MASCULINO);
        client.setMaritalStatus(MaritalStatus.SOLTEIRO);
        client.setClientType(ClientType.POTENCIAL);
        client.setSituation(Situation.FORMULARIO_PREENCHIDO);
        client.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);

        Client saved = clientRepository.save(client);
        createdClientId = saved.getId();

        long count =
                auditLogRepository
                        .findByEntityNameAndEntityIdOrderByPerformedAtDesc(
                                "Client", saved.getId(), Pageable.unpaged())
                        .getTotalElements();

        assertEquals(1, count);
    }

    @Test
    void softDeleteIsRecordedAsDeleteAndRestoreAsRestore() {
        // O defeito: cliente, compromisso, parcela, arquivo e entrevista "excluem" gravando
        // deleted_at e salvando - que para o JPA é um @PostUpdate como qualquer outro. A trilha
        // registrava UPDATE, indistinguível de uma correção de telefone, e não sobrava registro
        // de quem removeu o quê - que é o motivo pelo qual a tabela existe (V8__audit_log.sql).
        Client saved = clientRepository.save(novoCliente("000.000.002-72", "Exclusão Teste"));
        createdClientId = saved.getId();

        clientService.delete(saved.getId());
        assertEquals(AuditAction.DELETE, ultimaAcao(saved.getId()), "exclusão lógica vira DELETE");

        clientService.restore(saved.getId());
        assertEquals(AuditAction.RESTORE, ultimaAcao(saved.getId()), "restauração vira RESTORE");

        // E a distinção não se perdeu: a trilha inteira do cliente continua legível.
        List<AuditAction> acoes = acoes(saved.getId());
        assertTrue(acoes.contains(AuditAction.CREATE), "a criação continua registrada: " + acoes);
        assertTrue(acoes.contains(AuditAction.DELETE), "a exclusão ficou registrada: " + acoes);
        assertTrue(acoes.contains(AuditAction.RESTORE), "a restauração ficou registrada: " + acoes);
    }

    @Test
    void anOrdinaryEditIsStillRecordedAsUpdate() {
        // A intenção é declarada só na exclusão e na restauração; edição comum não declara nada e
        // tem de continuar caindo em UPDATE. Sem este teste, transformar tudo em DELETE passaria.
        Client saved = clientRepository.save(novoCliente("000.000.003-53", "Edição Teste"));
        createdClientId = saved.getId();

        saved.setProfession("Costureira");
        clientRepository.saveAndFlush(saved);

        assertEquals(AuditAction.UPDATE, ultimaAcao(saved.getId()));
    }

    private List<AuditAction> acoes(UUID clientId) {
        return auditLogRepository
                .findByEntityNameAndEntityIdOrderByPerformedAtDesc(
                        "Client", clientId, Pageable.unpaged())
                .map(AuditLog::getAction)
                .getContent();
    }

    private AuditAction ultimaAcao(UUID clientId) {
        List<AuditAction> acoes = acoes(clientId);
        assertTrue(!acoes.isEmpty(), "nenhuma linha de auditoria para " + clientId);
        return acoes.get(0);
    }

    private static Client novoCliente(String cpf, String nome) {
        Client client = new Client();
        client.setFullName(nome);
        client.setBirthDate(LocalDate.of(1990, 1, 1));
        client.setCpf(cpf);
        client.setMotherName("Mãe Teste");
        client.setMobilePhone("+5511900000000");
        client.setInssPassword("senha123");
        client.setGender(Gender.MASCULINO);
        client.setMaritalStatus(MaritalStatus.SOLTEIRO);
        client.setClientType(ClientType.POTENCIAL);
        client.setSituation(Situation.FORMULARIO_PREENCHIDO);
        client.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        return client;
    }
}
