package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lawfirm.law.firm.dto.ClientProfessionalDataRequestDTO;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

@DisplayName("Aba profissional: documento segue as mesmas regras do cadastro")
class ClientProfessionalDataIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ClientRepository clientRepository;
    @Autowired private ClientService clientService;

    private Client savedClient() {
        Client client = TestFixtures.client(null, "Cliente da Aba Profissional");
        client.setCpf(TestFixtures.uniqueDocumentDigits());
        return clientRepository.save(client);
    }

    private static ClientProfessionalDataRequestDTO withMaskedNitPis(String digits) {
        ClientProfessionalDataRequestDTO dto = new ClientProfessionalDataRequestDTO();
        dto.setNitPis(digits.substring(0, 3) + "." + digits.substring(3));
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("grava o documento só com dígitos")
    void normalizesDocuments() {
        UUID clientId = savedClient().getId();
        String digits = TestFixtures.uniqueDocumentDigits();

        clientService.updateProfessionalData(clientId, withMaskedNitPis(digits));

        assertEquals(digits, clientRepository.findById(clientId).orElseThrow().getNitPis());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("recusa documento de outro cliente, mesmo com outra máscara")
    void refusesDuplicateDocuments() {
        String digits = TestFixtures.uniqueDocumentDigits();
        Client owner = savedClient();
        owner.setNitPis(digits);
        clientRepository.save(owner);
        UUID otherClientId = savedClient().getId();
        ClientProfessionalDataRequestDTO dto = withMaskedNitPis(digits);

        ValidationException refused =
                assertThrows(
                        ValidationException.class,
                        () -> clientService.updateProfessionalData(otherClientId, dto));

        assertEquals("nitPis", refused.getField());
    }
}
