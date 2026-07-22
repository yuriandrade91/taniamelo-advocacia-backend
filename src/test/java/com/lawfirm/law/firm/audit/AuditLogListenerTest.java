package com.lawfirm.law.firm.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A gravação da auditoria acontece em afterCommit (ver {@link AuditLogListener}), então estes
 * testes não podem rodar dentro de uma transação de teste que sempre faz rollback (afterCommit
 * nunca dispararia) - cada teste commita de verdade e limpa os próprios dados em @AfterEach.
 */
@SpringBootTest
class AuditLogListenerTest {

    @Autowired private ClientRepository clientRepository;

    @Autowired private AuditLogRepository auditLogRepository;

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
}
