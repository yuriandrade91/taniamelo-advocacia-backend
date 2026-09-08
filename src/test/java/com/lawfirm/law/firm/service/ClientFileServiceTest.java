package com.lawfirm.law.firm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUploadMetadataDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationUploadMetadataDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.ClientFile;
import com.lawfirm.law.firm.model.DocumentType;
import com.lawfirm.law.firm.model.FileKind;
import com.lawfirm.law.firm.repository.ClientFileRepository;
import com.lawfirm.law.firm.repository.ClientRepository;
import com.lawfirm.law.firm.security.UserPrincipal;
import com.lawfirm.law.firm.storage.FileDownload;
import com.lawfirm.law.firm.storage.FileStorageService;
import com.lawfirm.law.firm.storage.LoadedFile;
import com.lawfirm.law.firm.storage.StoredFile;
import com.lawfirm.law.firm.support.TestFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClientFileService: documentos e simulações na mesma collection")
class ClientFileServiceTest {

    private static final UUID FILE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Mock private ClientFileRepository repository;
    @Mock private ClientRepository clientRepository;
    @Mock private FileStorageService fileStorageService;

    private ClientFileService service;

    @BeforeEach
    void setUp() {
        service = new ClientFileService(repository, clientRepository, fileStorageService);
        when(clientRepository.findById(TestFixtures.CLIENT_ID))
                .thenReturn(Optional.of(TestFixtures.client()));
        when(repository.save(any(ClientFile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAndFlush(any(ClientFile.class))).thenAnswer(i -> i.getArgument(0));
        when(fileStorageService.store(any(), anyString()))
                .thenReturn(new StoredFile("clients/x/a.pdf", "application/pdf", 1234L));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        UserPrincipal principal = new UserPrincipal(TestFixtures.user());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private MultipartFile pdf() {
        return new MockMultipartFile("files", "cnis.pdf", "application/pdf", "x".getBytes());
    }

    private ClientFile file(FileKind kind) {
        ClientFile entity = new ClientFile();
        entity.setId(FILE_ID);
        entity.setClient(TestFixtures.client());
        entity.setKind(kind);
        entity.setOriginalFilename("cnis.pdf");
        entity.setStorageKey("clients/x/a.pdf");
        entity.setMimeType("application/pdf");
        entity.setFileSizeBytes(1234L);
        if (kind == FileKind.DOCUMENT) {
            entity.setDocumentType(DocumentType.DOCUMENTOS_MEDICOS);
        } else {
            entity.setIsPrincipal(false);
        }
        return entity;
    }

    private ClientFileDocumentUploadMetadataDTO documentMetadata(String type) {
        ClientFileDocumentUploadMetadataDTO meta = new ClientFileDocumentUploadMetadataDTO();
        meta.setDocumentType(type);
        meta.setNotes("observação");
        return meta;
    }

    @Nested
    @DisplayName("upload de documentos")
    class UploadDocuments {

        @Test
        @DisplayName("grava cada arquivo com seu tipo, chave de storage e autor")
        void uploadsEachFileWithItsMetadata() {
            authenticate();

            List<ClientFileDocumentResponseDTO> created =
                    service.uploadDocuments(
                            TestFixtures.CLIENT_ID,
                            List.of(pdf(), pdf()),
                            List.of(
                                    documentMetadata("Documentos médicos"),
                                    documentMetadata("OUTROS")));

            assertEquals(2, created.size());
            ArgumentCaptor<ClientFile> saved = ArgumentCaptor.forClass(ClientFile.class);
            verify(repository, times(2)).save(saved.capture());

            ClientFile first = saved.getAllValues().get(0);
            assertEquals(FileKind.DOCUMENT, first.getKind());
            assertEquals(DocumentType.DOCUMENTOS_MEDICOS, first.getDocumentType());
            assertEquals("clients/x/a.pdf", first.getStorageKey());
            assertEquals("application/pdf", first.getMimeType());
            assertEquals(1234L, first.getFileSizeBytes());
            assertEquals("cnis.pdf", first.getOriginalFilename());
            assertEquals("observação", first.getNotes());
            assertEquals(TestFixtures.USER_ID, first.getUploadedBy());
            assertEquals(DocumentType.OUTROS, saved.getAllValues().get(1).getDocumentType());

            verify(fileStorageService, times(2))
                    .store(any(), eq("clients/" + TestFixtures.CLIENT_ID + "/documents"));
        }

        @Test
        @DisplayName("a URL de download é montada a partir do cliente e do arquivo")
        void buildsDownloadUrl() {
            ClientFileDocumentResponseDTO dto =
                    service.uploadDocuments(
                                    TestFixtures.CLIENT_ID,
                                    List.of(pdf()),
                                    List.of(documentMetadata("Outros")))
                            .get(0);

            assertTrue(
                    dto.getDownloadUrl().startsWith("/api/v1/clients/" + TestFixtures.CLIENT_ID));
            assertTrue(dto.getDownloadUrl().endsWith("/download"));
        }

        @Test
        @DisplayName("lista de arquivos vazia ou nula vira 400")
        void emptyFileListThrows() {
            List<ClientFileDocumentUploadMetadataDTO> metadata = List.of();

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.uploadDocuments(
                                            TestFixtures.CLIENT_ID, List.of(), metadata));
            assertEquals("files", ex.getField());
            assertEquals(ValidationErrorCode.REQUIRED_FIELD, ex.getValidationErrorCode());

            assertThrows(
                    ValidationException.class,
                    () -> service.uploadDocuments(TestFixtures.CLIENT_ID, null, metadata));
        }

        @Test
        @DisplayName("metadados em quantidade diferente dos arquivos vira 400")
        void mismatchedMetadataThrows() {
            List<MultipartFile> files = List.of(pdf(), pdf());
            List<ClientFileDocumentUploadMetadataDTO> metadata =
                    List.of(documentMetadata("Outros"));

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.uploadDocuments(TestFixtures.CLIENT_ID, files, metadata));
            assertEquals("metadata", ex.getField());
        }

        @Test
        @DisplayName("metadados nulos viram 400")
        void nullMetadataThrows() {
            List<MultipartFile> files = List.of(pdf());
            assertEquals(
                    "metadata",
                    assertThrows(
                                    ValidationException.class,
                                    () ->
                                            service.uploadDocuments(
                                                    TestFixtures.CLIENT_ID, files, null))
                            .getField());
        }

        @Test
        @DisplayName("tipo de documento é obrigatório no upload")
        void documentTypeIsRequired() {
            List<MultipartFile> files = List.of(pdf());
            List<ClientFileDocumentUploadMetadataDTO> metadata = List.of(documentMetadata(null));

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.uploadDocuments(TestFixtures.CLIENT_ID, files, metadata));
            assertEquals("documentType", ex.getField());
            assertEquals(ValidationErrorCode.REQUIRED_FIELD, ex.getValidationErrorCode());
        }

        @Test
        @DisplayName("tipo de documento inválido vira 400")
        void invalidDocumentTypeThrows() {
            List<MultipartFile> files = List.of(pdf());
            List<ClientFileDocumentUploadMetadataDTO> metadata =
                    List.of(documentMetadata("Certidão marciana"));

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.uploadDocuments(TestFixtures.CLIENT_ID, files, metadata));
            assertEquals("documentType", ex.getField());
            assertEquals(ValidationErrorCode.INVALID_ENUM_VALUE, ex.getValidationErrorCode());
        }

        @Test
        @DisplayName("mime type fora da whitelist é recusado antes de gravar")
        void rejectsDisallowedMimeType() {
            List<MultipartFile> files =
                    List.of(
                            new MockMultipartFile(
                                    "files",
                                    "virus.exe",
                                    "application/x-msdownload",
                                    "x".getBytes()));
            List<ClientFileDocumentUploadMetadataDTO> metadata =
                    List.of(documentMetadata("Outros"));

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () -> service.uploadDocuments(TestFixtures.CLIENT_ID, files, metadata));
            assertEquals("files", ex.getField());
            assertTrue(ex.getMessage().contains("PDF, PNG, JPEG"));
            verify(fileStorageService, never()).store(any(), anyString());
        }

        @Test
        @DisplayName("content-type ausente é recusado")
        void rejectsMissingContentType() {
            List<MultipartFile> files =
                    List.of(new MockMultipartFile("files", "a.pdf", null, "x".getBytes()));
            List<ClientFileDocumentUploadMetadataDTO> metadata =
                    List.of(documentMetadata("Outros"));

            assertThrows(
                    ValidationException.class,
                    () -> service.uploadDocuments(TestFixtures.CLIENT_ID, files, metadata));
        }

        @Test
        @DisplayName("PNG e JPEG são aceitos como documento")
        void acceptsImagesAsDocuments() {
            for (String mime : List.of("image/png", "image/jpeg", "IMAGE/PNG")) {
                assertEquals(
                        1,
                        service.uploadDocuments(
                                        TestFixtures.CLIENT_ID,
                                        List.of(
                                                new MockMultipartFile(
                                                        "files", "a", mime, "x".getBytes())),
                                        List.of(documentMetadata("Outros")))
                                .size(),
                        mime);
            }
        }
    }

    @Nested
    @DisplayName("listagem e edição de documentos")
    class DocumentReads {

        @Test
        @DisplayName(
                "sem filtro, lista todos os documentos ativos do mais recente para o mais antigo")
        void listsAllDocuments() {
            when(repository.findByClient_IdAndKindAndDeletedAtIsNull(
                            any(), eq(FileKind.DOCUMENT), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(file(FileKind.DOCUMENT))));

            assertEquals(
                    1,
                    service.listDocuments(TestFixtures.CLIENT_ID, null, 1, 10).getContent().size());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository)
                    .findByClient_IdAndKindAndDeletedAtIsNull(any(), any(), pageable.capture());
            assertEquals(
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC, "uploadedAt"),
                    pageable.getValue().getSort());
        }

        @Test
        @DisplayName("com filtro, usa a consulta por tipo de documento")
        void listsFilteredByType() {
            when(repository.findByClient_IdAndKindAndDocumentTypeAndDeletedAtIsNull(
                            any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(file(FileKind.DOCUMENT))));

            service.listDocuments(TestFixtures.CLIENT_ID, "Documentos médicos", 1, 10);

            verify(repository)
                    .findByClient_IdAndKindAndDocumentTypeAndDeletedAtIsNull(
                            eq(TestFixtures.CLIENT_ID),
                            eq(FileKind.DOCUMENT),
                            eq(DocumentType.DOCUMENTOS_MEDICOS),
                            any(Pageable.class));
            verify(repository, never())
                    .findByClient_IdAndKindAndDeletedAtIsNull(any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("filtro em branco é tratado como ausência de filtro")
        void blankFilterMeansNoFilter() {
            when(repository.findByClient_IdAndKindAndDeletedAtIsNull(
                            any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.listDocuments(TestFixtures.CLIENT_ID, "   ", 1, 10);

            verify(repository)
                    .findByClient_IdAndKindAndDeletedAtIsNull(any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("filtro com tipo inválido vira 400")
        void invalidFilterThrows() {
            assertThrows(
                    ValidationException.class,
                    () -> service.listDocuments(TestFixtures.CLIENT_ID, "inexistente", 1, 10));
        }

        @Test
        @DisplayName("get devolve o documento mapeado")
        void getMapsDocument() {
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(
                            FILE_ID, TestFixtures.CLIENT_ID, FileKind.DOCUMENT))
                    .thenReturn(Optional.of(file(FileKind.DOCUMENT)));

            ClientFileDocumentResponseDTO dto =
                    service.getDocument(TestFixtures.CLIENT_ID, FILE_ID);

            assertEquals(FILE_ID, dto.getId());
            assertEquals("Documentos médicos", dto.getDocumentType());
            assertEquals("cnis.pdf", dto.getOriginalFilename());
            assertEquals(1234L, dto.getFileSizeBytes());
        }

        @Test
        @DisplayName("documento sem tipo devolve rótulo nulo")
        void nullDocumentTypeMapsToNull() {
            ClientFile entity = file(FileKind.DOCUMENT);
            entity.setDocumentType(null);
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(entity));

            assertNull(service.getDocument(TestFixtures.CLIENT_ID, FILE_ID).getDocumentType());
        }

        @Test
        @DisplayName("documento inexistente estoura 404 nomeando 'Documento'")
        void missingDocumentThrows() {
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.empty());
            UUID unknown = UUID.randomUUID();

            NotFoundException ex =
                    assertThrows(
                            NotFoundException.class,
                            () -> service.getDocument(TestFixtures.CLIENT_ID, unknown));
            assertTrue(ex.getMessage().startsWith("Documento"));
        }

        @Test
        @DisplayName("update troca tipo e observações e marca o autor")
        void updateChangesTypeAndNotes() {
            ClientFile entity = file(FileKind.DOCUMENT);
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(entity));
            authenticate();

            ClientFileDocumentUpdateRequestDTO dto = new ClientFileDocumentUpdateRequestDTO();
            dto.setDocumentType("Outros");
            dto.setNotes("nova observação");

            service.updateDocument(TestFixtures.CLIENT_ID, FILE_ID, dto);

            assertEquals(DocumentType.OUTROS, entity.getDocumentType());
            assertEquals("nova observação", entity.getNotes());
            assertEquals(TestFixtures.USER_ID, entity.getUpdatedBy());
        }

        @Test
        @DisplayName("update vazio preserva os metadados atuais")
        void emptyUpdateKeepsCurrentMetadata() {
            ClientFile entity = file(FileKind.DOCUMENT);
            entity.setNotes("original");
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(entity));

            service.updateDocument(
                    TestFixtures.CLIENT_ID, FILE_ID, new ClientFileDocumentUpdateRequestDTO());

            assertEquals(DocumentType.DOCUMENTOS_MEDICOS, entity.getDocumentType());
            assertEquals("original", entity.getNotes());
        }
    }

    @Nested
    @DisplayName("simulações e a regra de 'principal'")
    class Simulations {

        private ClientFileSimulationUploadMetadataDTO metadata() {
            ClientFileSimulationUploadMetadataDTO meta =
                    new ClientFileSimulationUploadMetadataDTO();
            meta.setSimulationDate(LocalDate.of(2026, 6, 1));
            meta.setVersion("v1.0.2");
            meta.setVinculos(7);
            meta.setNotes("simulação inicial");
            return meta;
        }

        @Test
        @DisplayName("a simulação enviada vira a principal automaticamente")
        void newestUploadBecomesPrincipal() {
            when(repository.findFirstByClient_IdAndKindAndIsPrincipalTrueAndDeletedAtIsNull(
                            any(), any()))
                    .thenReturn(Optional.empty());
            authenticate();

            List<ClientFileSimulationResponseDTO> created =
                    service.uploadSimulations(
                            TestFixtures.CLIENT_ID, List.of(pdf()), List.of(metadata()));

            assertEquals(1, created.size());
            assertTrue(created.get(0).getIsPrincipal());
            assertEquals("v1.0.2", created.get(0).getVersion());
            assertEquals(7, created.get(0).getVinculos());
            assertEquals(LocalDate.of(2026, 6, 1), created.get(0).getSimulationDate());
            verify(fileStorageService)
                    .store(any(), eq("clients/" + TestFixtures.CLIENT_ID + "/simulations"));
        }

        @Test
        @DisplayName("a principal anterior é desmarcada com flush antes de gravar a nova")
        void previousPrincipalIsUnset() {
            ClientFile previous = file(FileKind.SIMULATION);
            previous.setIsPrincipal(true);
            when(repository.findFirstByClient_IdAndKindAndIsPrincipalTrueAndDeletedAtIsNull(
                            TestFixtures.CLIENT_ID, FileKind.SIMULATION))
                    .thenReturn(Optional.of(previous));

            service.uploadSimulations(TestFixtures.CLIENT_ID, List.of(pdf()), List.of(metadata()));

            assertFalse(previous.getIsPrincipal());
            verify(repository).saveAndFlush(previous);
        }

        @Test
        @DisplayName("simulação só aceita PDF")
        void simulationsAcceptOnlyPdf() {
            List<MultipartFile> files =
                    List.of(new MockMultipartFile("files", "a.png", "image/png", "x".getBytes()));
            List<ClientFileSimulationUploadMetadataDTO> metadata = List.of(metadata());

            ValidationException ex =
                    assertThrows(
                            ValidationException.class,
                            () ->
                                    service.uploadSimulations(
                                            TestFixtures.CLIENT_ID, files, metadata));
            assertTrue(ex.getMessage().contains("só aceita PDF"));
        }

        @Test
        @DisplayName("lote inválido é recusado igual ao de documentos")
        void invalidBatchThrows() {
            List<ClientFileSimulationUploadMetadataDTO> metadata = List.of(metadata());
            assertThrows(
                    ValidationException.class,
                    () -> service.uploadSimulations(TestFixtures.CLIENT_ID, List.of(), metadata));
            List<MultipartFile> files = List.of(pdf(), pdf());
            assertThrows(
                    ValidationException.class,
                    () -> service.uploadSimulations(TestFixtures.CLIENT_ID, files, metadata));
        }

        @Test
        @DisplayName("listagem traz as ativas, mais recente primeiro")
        void listsSimulations() {
            when(repository.findByClient_IdAndKindAndDeletedAtIsNull(
                            any(), eq(FileKind.SIMULATION), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(file(FileKind.SIMULATION))));

            assertEquals(
                    1, service.listSimulations(TestFixtures.CLIENT_ID, 0, 0).getContent().size());

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository)
                    .findByClient_IdAndKindAndDeletedAtIsNull(any(), any(), pageable.capture());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(10, pageable.getValue().getPageSize());
        }

        @Test
        @DisplayName("get devolve a simulação mapeada")
        void getMapsSimulation() {
            ClientFile entity = file(FileKind.SIMULATION);
            entity.setVersion("v2");
            entity.setVinculos(3);
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(
                            FILE_ID, TestFixtures.CLIENT_ID, FileKind.SIMULATION))
                    .thenReturn(Optional.of(entity));

            ClientFileSimulationResponseDTO dto =
                    service.getSimulation(TestFixtures.CLIENT_ID, FILE_ID);

            assertEquals("v2", dto.getVersion());
            assertEquals(3, dto.getVinculos());
            assertFalse(dto.getIsPrincipal());
        }

        @Test
        @DisplayName("simulação inexistente estoura 404 nomeando 'Simulação'")
        void missingSimulationThrows() {
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.empty());
            UUID unknown = UUID.randomUUID();

            NotFoundException ex =
                    assertThrows(
                            NotFoundException.class,
                            () -> service.getSimulation(TestFixtures.CLIENT_ID, unknown));
            assertTrue(ex.getMessage().startsWith("Simulação"));
        }

        @Test
        @DisplayName("update aplica só os campos enviados")
        void updateAppliesOnlyProvidedFields() {
            ClientFile entity = file(FileKind.SIMULATION);
            entity.setVersion("v1");
            entity.setVinculos(1);
            entity.setNotes("antiga");
            entity.setSimulationDate(LocalDate.of(2026, 1, 1));
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(entity));
            authenticate();

            ClientFileSimulationUpdateRequestDTO dto = new ClientFileSimulationUpdateRequestDTO();
            dto.setVersion("v3");

            service.updateSimulation(TestFixtures.CLIENT_ID, FILE_ID, dto);

            assertEquals("v3", entity.getVersion());
            assertEquals(1, entity.getVinculos(), "campo não enviado é preservado");
            assertEquals("antiga", entity.getNotes());
            assertEquals(LocalDate.of(2026, 1, 1), entity.getSimulationDate());
            assertEquals(TestFixtures.USER_ID, entity.getUpdatedBy());
        }

        @Test
        @DisplayName("update completo troca todos os metadados")
        void updateCanReplaceEverything() {
            ClientFile entity = file(FileKind.SIMULATION);
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(entity));

            ClientFileSimulationUpdateRequestDTO dto = new ClientFileSimulationUpdateRequestDTO();
            dto.setSimulationDate(LocalDate.of(2027, 2, 2));
            dto.setVersion("v9");
            dto.setVinculos(42);
            dto.setNotes("revisada");

            service.updateSimulation(TestFixtures.CLIENT_ID, FILE_ID, dto);

            assertEquals(LocalDate.of(2027, 2, 2), entity.getSimulationDate());
            assertEquals("v9", entity.getVersion());
            assertEquals(42, entity.getVinculos());
            assertEquals("revisada", entity.getNotes());
        }

        @Test
        @DisplayName("marcar como principal desmarca a anterior")
        void markPrincipalUnsetsThePrevious() {
            ClientFile target = file(FileKind.SIMULATION);
            ClientFile previous = file(FileKind.SIMULATION);
            previous.setId(UUID.randomUUID());
            previous.setIsPrincipal(true);

            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(target));
            when(repository.findFirstByClient_IdAndKindAndIsPrincipalTrueAndDeletedAtIsNull(
                            any(), any()))
                    .thenReturn(Optional.of(previous));
            authenticate();

            assertTrue(
                    service.markSimulationPrincipal(TestFixtures.CLIENT_ID, FILE_ID)
                            .getIsPrincipal());
            assertFalse(previous.getIsPrincipal());
            assertEquals(TestFixtures.USER_ID, target.getUpdatedBy());
        }

        @Test
        @DisplayName("marcar a que já é principal não faz nada")
        void markingTheCurrentPrincipalIsANoOp() {
            ClientFile target = file(FileKind.SIMULATION);
            target.setIsPrincipal(true);
            when(repository.findByIdAndClient_IdAndKindAndDeletedAtIsNull(any(), any(), any()))
                    .thenReturn(Optional.of(target));

            assertTrue(
                    service.markSimulationPrincipal(TestFixtures.CLIENT_ID, FILE_ID)
                            .getIsPrincipal());
            verify(repository, never()).save(any());
            verify(repository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("download e exclusão (funcionam para os dois tipos)")
    class DownloadAndDelete {

        @Test
        @DisplayName("download resolve o tipo pelo próprio registro")
        void downloadWorksForAnyKind() {
            ClientFile entity = file(FileKind.SIMULATION);
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(FILE_ID, TestFixtures.CLIENT_ID))
                    .thenReturn(Optional.of(entity));
            when(fileStorageService.load("clients/x/a.pdf"))
                    .thenReturn(
                            new LoadedFile(
                                    new ByteArrayResource("x".getBytes()), "application/pdf"));

            FileDownload download = service.downloadFile(TestFixtures.CLIENT_ID, FILE_ID);

            assertEquals("cnis.pdf", download.originalFilename());
            assertEquals("application/pdf", download.mimeType());
            assertEquals(1234L, download.sizeBytes());
            assertNotNull(download.resource());
        }

        @Test
        @DisplayName("download de arquivo inexistente estoura 404 nomeando 'Arquivo'")
        void downloadOfMissingFileThrows() {
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.empty());
            UUID unknown = UUID.randomUUID();

            NotFoundException ex =
                    assertThrows(
                            NotFoundException.class,
                            () -> service.downloadFile(TestFixtures.CLIENT_ID, unknown));
            assertTrue(ex.getMessage().startsWith("Arquivo"));
        }

        @Test
        @DisplayName("delete de documento é soft e não mexe em principal")
        void deletingADocumentIsSoft() {
            ClientFile entity = file(FileKind.DOCUMENT);
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.of(entity));
            authenticate();

            service.deleteFile(TestFixtures.CLIENT_ID, FILE_ID);

            assertNotNull(entity.getDeletedAt());
            assertEquals(TestFixtures.USER_ID, entity.getUpdatedBy());
            verify(repository, never())
                    .findFirstByClient_IdAndKindAndDeletedAtIsNullOrderByUploadedAtDesc(
                            any(), any());
            verify(repository, never()).delete(any());
        }

        @Test
        @DisplayName("excluir a simulação principal promove a mais recente restante")
        void deletingThePrincipalPromotesTheNewestRemaining() {
            ClientFile principal = file(FileKind.SIMULATION);
            principal.setIsPrincipal(true);
            ClientFile remaining = file(FileKind.SIMULATION);
            remaining.setId(UUID.randomUUID());
            remaining.setIsPrincipal(false);

            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.of(principal));
            when(repository.findFirstByClient_IdAndKindAndDeletedAtIsNullOrderByUploadedAtDesc(
                            TestFixtures.CLIENT_ID, FileKind.SIMULATION))
                    .thenReturn(Optional.of(remaining));
            authenticate();

            service.deleteFile(TestFixtures.CLIENT_ID, FILE_ID);

            assertFalse(principal.getIsPrincipal());
            assertNotNull(principal.getDeletedAt());
            assertTrue(remaining.getIsPrincipal());
            assertEquals(TestFixtures.USER_ID, remaining.getUpdatedBy());
        }

        @Test
        @DisplayName("excluir a última simulação não quebra a promoção")
        void deletingTheLastSimulationIsSafe() {
            ClientFile principal = file(FileKind.SIMULATION);
            principal.setIsPrincipal(true);
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.of(principal));
            when(repository.findFirstByClient_IdAndKindAndDeletedAtIsNullOrderByUploadedAtDesc(
                            any(), any()))
                    .thenReturn(Optional.empty());

            service.deleteFile(TestFixtures.CLIENT_ID, FILE_ID);

            assertNotNull(principal.getDeletedAt());
            verify(repository, times(1)).save(principal);
        }

        @Test
        @DisplayName("excluir uma simulação não-principal não promove ninguém")
        void deletingANonPrincipalSimulationDoesNotPromote() {
            ClientFile entity = file(FileKind.SIMULATION);
            entity.setIsPrincipal(false);
            when(repository.findByIdAndClient_IdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.of(entity));

            service.deleteFile(TestFixtures.CLIENT_ID, FILE_ID);

            verify(repository, never())
                    .findFirstByClient_IdAndKindAndDeletedAtIsNullOrderByUploadedAtDesc(
                            any(), any());
        }
    }

    @Test
    @DisplayName("cliente inexistente estoura 404 em todas as operações de arquivo")
    void missingClientThrowsEverywhere() {
        UUID unknown = UUID.randomUUID();
        when(clientRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.listDocuments(unknown, null, 1, 10));
        assertThrows(NotFoundException.class, () -> service.getDocument(unknown, FILE_ID));
        assertThrows(NotFoundException.class, () -> service.listSimulations(unknown, 1, 10));
        assertThrows(NotFoundException.class, () -> service.getSimulation(unknown, FILE_ID));
        assertThrows(NotFoundException.class, () -> service.downloadFile(unknown, FILE_ID));
        assertThrows(NotFoundException.class, () -> service.deleteFile(unknown, FILE_ID));
        assertThrows(
                NotFoundException.class, () -> service.markSimulationPrincipal(unknown, FILE_ID));
        assertSame(unknown, unknown);
    }
}
