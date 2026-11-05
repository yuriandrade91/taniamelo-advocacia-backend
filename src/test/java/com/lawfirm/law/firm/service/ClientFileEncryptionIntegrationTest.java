package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUploadMetadataDTO;
import com.lawfirm.law.firm.model.BenefitType;
import com.lawfirm.law.firm.model.Client;
import com.lawfirm.law.firm.model.ClientFile;
import com.lawfirm.law.firm.model.ClientType;
import com.lawfirm.law.firm.model.Gender;
import com.lawfirm.law.firm.model.MaritalStatus;
import com.lawfirm.law.firm.model.Situation;
import com.lawfirm.law.firm.repository.ClientFileRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.storage.FileDownload;
import com.lawfirm.law.firm.storage.FileEncryption;
import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import com.lawfirm.law.firm.support.TestFixtures;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

/**
 * A montagem real do Spring, do service ao disco: prova que o documento do cliente chega ao storage
 * cifrado e volta original no download — os testes de unidade provam cada peça, este prova que o
 * {@code ClientFileService} de produção usa a peça certa.
 */
@DisplayName("Arquivos de cliente: cifrados no storage, originais no download")
class ClientFileEncryptionIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] LAUDO =
            "%PDF-1.7 laudo pericial de Joana Teste".getBytes(StandardCharsets.UTF_8);

    @Autowired private ClientFileService service;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ClientFileRepository fileRepository;

    @Value("${app.storage.local.base-path}")
    private String basePath;

    private UUID clientId;

    @BeforeEach
    void createClient() {
        Client client = new Client();
        client.setFullName("Joana Teste Arquivo");
        client.setBirthDate(LocalDate.of(1970, 5, 20));
        client.setCpf(TestFixtures.uniqueDocumentDigits());
        client.setMotherName("Mãe Teste");
        client.setMobilePhone("+5511900000000");
        client.setInssPassword("senha-de-teste");
        client.setGender(Gender.FEMININO);
        client.setMaritalStatus(MaritalStatus.SOLTEIRO);
        client.setClientType(ClientType.POTENCIAL);
        client.setSituation(Situation.FORMULARIO_PREENCHIDO);
        client.setBenefit(BenefitType.APOSENTADORIA_POR_IDADE);
        clientId = clientRepository.save(client).getId();
    }

    @Test
    @DisplayName("o disco guarda o conteúdo cifrado; o banco e o download, o original")
    void documentIsEncryptedAtRest() throws Exception {
        ClientFileDocumentUploadMetadataDTO metadata = new ClientFileDocumentUploadMetadataDTO();
        metadata.setDocumentType("OUTROS");
        List<ClientFileDocumentResponseDTO> created =
                service.uploadDocuments(
                        clientId,
                        List.of(
                                new MockMultipartFile(
                                        "files", "laudo.pdf", "application/pdf", LAUDO)),
                        List.of(metadata));
        UUID fileId = created.get(0).getId();
        ClientFile saved = fileRepository.findById(fileId).orElseThrow();

        byte[] onDisk = Files.readAllBytes(Path.of(basePath).resolve(saved.getStorageKey()));
        assertTrue(FileEncryption.isEncrypted(onDisk));
        assertFalse(new String(onDisk, StandardCharsets.ISO_8859_1).contains("Joana"));
        assertEquals("application/pdf", saved.getMimeType());
        assertEquals(LAUDO.length, saved.getFileSizeBytes());

        FileDownload download = service.downloadFile(clientId, fileId);
        assertEquals("application/pdf", download.mimeType());
        assertEquals(LAUDO.length, download.sizeBytes());
        try (InputStream in = download.resource().getInputStream()) {
            assertArrayEquals(LAUDO, in.readAllBytes());
        }
    }
}
