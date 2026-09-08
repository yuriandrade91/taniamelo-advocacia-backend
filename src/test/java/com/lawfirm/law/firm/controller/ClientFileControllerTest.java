package com.lawfirm.law.firm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationResponseDTO;
import com.lawfirm.law.firm.exception.GlobalExceptionHandler;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.service.ClientFileService;
import com.lawfirm.law.firm.storage.FileDownload;
import com.lawfirm.law.firm.storage.FileStorageException;
import com.lawfirm.law.firm.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClientFileController: upload em lote, download e exclusão de arquivos")
class ClientFileControllerTest {

    private static final UUID CLIENT = TestFixtures.CLIENT_ID;
    private static final UUID FILE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Mock private ClientFileService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ClientFileController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    private ClientFileDocumentResponseDTO documentDto() {
        ClientFileDocumentResponseDTO dto = new ClientFileDocumentResponseDTO();
        dto.setId(FILE_ID);
        dto.setDocumentType("Documentos médicos");
        dto.setOriginalFilename("laudo.pdf");
        dto.setMimeType("application/pdf");
        dto.setFileSizeBytes(1234L);
        dto.setDownloadUrl("/api/v1/clients/" + CLIENT + "/files/" + FILE_ID + "/download");
        return dto;
    }

    private ClientFileSimulationResponseDTO simulationDto() {
        ClientFileSimulationResponseDTO dto = new ClientFileSimulationResponseDTO();
        dto.setId(FILE_ID);
        dto.setOriginalFilename("cnis.pdf");
        dto.setMimeType("application/pdf");
        dto.setFileSizeBytes(4321L);
        dto.setSimulationDate(LocalDate.of(2026, 6, 1));
        dto.setVersion("v1.0.2");
        dto.setVinculos(7);
        dto.setIsPrincipal(true);
        return dto;
    }

    private MockMultipartFile filePart(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "conteudo".getBytes());
    }

    private MockMultipartFile metadataPart(String json) {
        return new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("documentos")
    class Documents {

        @Test
        @DisplayName("upload em lote devolve 201 com a lista criada")
        void uploadReturns201() throws Exception {
            when(service.uploadDocuments(eq(CLIENT), any(), any()))
                    .thenReturn(List.of(documentDto(), documentDto()));

            mockMvc.perform(
                            multipart("/api/v1/clients/{clientId}/files/documents", CLIENT)
                                    .file(filePart("laudo.pdf"))
                                    .file(filePart("exame.pdf"))
                                    .file(
                                            metadataPart(
                                                    "[{\"documentType\":\"Documentos médicos\"},"
                                                            + "{\"documentType\":\"Outros\"}]")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", Matchers.hasSize(2)))
                    .andExpect(jsonPath("$.data[0].documentType").value("Documentos médicos"))
                    .andExpect(
                            jsonPath("$.data[0].downloadUrl")
                                    .value(Matchers.endsWith("/download")));
        }

        @Test
        @DisplayName("upload sem a parte 'metadata' vira 400 REQUIRED_FIELD")
        void uploadWithoutMetadataReturns400() throws Exception {
            mockMvc.perform(
                            multipart("/api/v1/clients/{clientId}/files/documents", CLIENT)
                                    .file(filePart("laudo.pdf")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("metadata"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED_FIELD"));
        }

        @Test
        @DisplayName("mime type não permitido propaga o 400 do service")
        void disallowedMimeTypeReturns400() throws Exception {
            when(service.uploadDocuments(any(), any(), any()))
                    .thenThrow(
                            new ValidationException(
                                    "files",
                                    ValidationErrorCode.INVALID_ENUM_VALUE,
                                    "Tipo de arquivo não permitido (aceitos: PDF, PNG, JPEG): text/html"));

            mockMvc.perform(
                            multipart("/api/v1/clients/{clientId}/files/documents", CLIENT)
                                    .file(filePart("pagina.html"))
                                    .file(metadataPart("[{\"documentType\":\"Outros\"}]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("files"));
        }

        @Test
        @DisplayName("listagem com e sem filtro por tipo")
        void listDocuments() throws Exception {
            when(service.listDocuments(eq(CLIENT), any(), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of(documentDto()), PageRequest.of(0, 10), 1));

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/documents", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].originalFilename").value("laudo.pdf"))
                    .andExpect(jsonPath("$.pagination.pageNumber").value(1));
            verify(service).listDocuments(CLIENT, null, 1, 10);

            mockMvc.perform(
                            get("/api/v1/clients/{clientId}/files/documents", CLIENT)
                                    .param("documentType", "Documentos médicos")
                                    .param("pageNumber", "3")
                                    .param("pageSize", "20"))
                    .andExpect(status().isOk());
            verify(service).listDocuments(CLIENT, "Documentos médicos", 3, 20);
        }

        @Test
        @DisplayName("detalhe de um documento")
        void getDocument() throws Exception {
            when(service.getDocument(CLIENT, FILE_ID)).thenReturn(documentDto());

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/documents/{id}", CLIENT, FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fileSizeBytes").value(1234));
        }

        @Test
        @DisplayName("PATCH de metadados devolve o documento atualizado")
        void patchDocument() throws Exception {
            when(service.updateDocument(eq(CLIENT), eq(FILE_ID), any())).thenReturn(documentDto());

            mockMvc.perform(
                            patch(
                                            "/api/v1/clients/{clientId}/files/documents/{id}",
                                            CLIENT,
                                            FILE_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"documentType\":\"Outros\",\"notes\":\"revisado\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("documento inexistente vira 404")
        void missingDocumentReturns404() throws Exception {
            when(service.getDocument(any(), any()))
                    .thenThrow(NotFoundException.of("Documento", FILE_ID));

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/documents/{id}", CLIENT, FILE_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("simulações")
    class Simulations {

        @Test
        @DisplayName("upload em lote devolve 201 marcando a principal")
        void uploadReturns201() throws Exception {
            when(service.uploadSimulations(eq(CLIENT), any(), any()))
                    .thenReturn(List.of(simulationDto()));

            mockMvc.perform(
                            multipart("/api/v1/clients/{clientId}/files/simulations", CLIENT)
                                    .file(filePart("cnis.pdf"))
                                    .file(
                                            metadataPart(
                                                    "[{\"simulationDate\":\"2026-06-01\",\"version\":\"v1.0.2\","
                                                            + "\"vinculos\":7}]")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data[0].isPrincipal").value(true))
                    .andExpect(jsonPath("$.data[0].version").value("v1.0.2"))
                    .andExpect(jsonPath("$.data[0].vinculos").value(7));
        }

        @Test
        @DisplayName("PDF é obrigatório - o 400 do service é propagado")
        void nonPdfReturns400() throws Exception {
            when(service.uploadSimulations(any(), any(), any()))
                    .thenThrow(
                            new ValidationException(
                                    "files",
                                    ValidationErrorCode.INVALID_ENUM_VALUE,
                                    "Simulação só aceita PDF: image/png"));

            mockMvc.perform(
                            multipart("/api/v1/clients/{clientId}/files/simulations", CLIENT)
                                    .file(
                                            new MockMultipartFile(
                                                    "files", "a.png", "image/png", "x".getBytes()))
                                    .file(metadataPart("[{}]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.errors[0].message")
                                    .value(Matchers.containsString("só aceita PDF")));
        }

        @Test
        @DisplayName("listagem e detalhe")
        void listAndGet() throws Exception {
            when(service.listSimulations(eq(CLIENT), anyInt(), anyInt()))
                    .thenReturn(new PageImpl<>(List.of(simulationDto()), PageRequest.of(0, 10), 1));
            when(service.getSimulation(CLIENT, FILE_ID)).thenReturn(simulationDto());

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/simulations", CLIENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].simulationDate").value("2026-06-01"));

            mockMvc.perform(
                            get(
                                    "/api/v1/clients/{clientId}/files/simulations/{id}",
                                    CLIENT,
                                    FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(FILE_ID.toString()));
        }

        @Test
        @DisplayName("PATCH de metadados e marcação de principal")
        void patchAndMarkPrincipal() throws Exception {
            when(service.updateSimulation(eq(CLIENT), eq(FILE_ID), any()))
                    .thenReturn(simulationDto());
            when(service.markSimulationPrincipal(CLIENT, FILE_ID)).thenReturn(simulationDto());

            mockMvc.perform(
                            patch(
                                            "/api/v1/clients/{clientId}/files/simulations/{id}",
                                            CLIENT,
                                            FILE_ID)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"version\":\"v2\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(
                            patch(
                                    "/api/v1/clients/{clientId}/files/simulations/{id}/principal",
                                    CLIENT,
                                    FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isPrincipal").value(true));
        }
    }

    @Nested
    @DisplayName("download e exclusão (kind-agnostic)")
    class DownloadAndDelete {

        @Test
        @DisplayName("download devolve o arquivo com Content-Disposition, tipo e tamanho")
        void downloadReturnsAttachment() throws Exception {
            byte[] bytes = "conteudo-do-pdf".getBytes(StandardCharsets.UTF_8);
            when(service.downloadFile(CLIENT, FILE_ID))
                    .thenReturn(
                            new FileDownload(
                                    new ByteArrayResource(bytes),
                                    "application/pdf",
                                    "laudo médico.pdf",
                                    bytes.length));

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/{id}/download", CLIENT, FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Length", String.valueOf(bytes.length)))
                    .andExpect(
                            header().string(
                                            "Content-Disposition",
                                            Matchers.containsString("attachment")))
                    .andExpect(content().bytes(bytes));
        }

        @Test
        @DisplayName("arquivo sem nome original cai no nome padrão 'arquivo'")
        void downloadWithoutOriginalNameUsesFallback() throws Exception {
            when(service.downloadFile(CLIENT, FILE_ID))
                    .thenReturn(
                            new FileDownload(
                                    new ByteArrayResource("x".getBytes()),
                                    "application/pdf",
                                    null,
                                    1L));

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/{id}/download", CLIENT, FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(
                            header().string(
                                            "Content-Disposition",
                                            Matchers.containsString("arquivo")));
        }

        @Test
        @DisplayName("arquivo inexistente vira 404")
        void downloadOfMissingFileReturns404() throws Exception {
            when(service.downloadFile(any(), any()))
                    .thenThrow(NotFoundException.of("Arquivo", FILE_ID));

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/{id}/download", CLIENT, FILE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("falha de storage vira 500 sem expor o caminho do arquivo")
        void storageFailureReturns500() throws Exception {
            when(service.downloadFile(any(), any()))
                    .thenThrow(new FileStorageException("/var/data/storage/clients/x.pdf sumiu"));

            mockMvc.perform(get("/api/v1/clients/{clientId}/files/{id}/download", CLIENT, FILE_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errors[0].code").value("FILE_STORAGE_ERROR"))
                    .andExpect(
                            jsonPath("$.errors[0].message")
                                    .value(Matchers.not(Matchers.containsString("/var/data"))));
        }

        @Test
        @DisplayName("DELETE devolve 204 e funciona para os dois tipos")
        void deleteReturns204() throws Exception {
            mockMvc.perform(delete("/api/v1/clients/{clientId}/files/{id}", CLIENT, FILE_ID))
                    .andExpect(status().isNoContent());
            verify(service).deleteFile(CLIENT, FILE_ID);
        }

        @Test
        @DisplayName("DELETE de arquivo inexistente vira 404")
        void deleteOfMissingFileReturns404() throws Exception {
            doThrow(NotFoundException.of("Arquivo", FILE_ID))
                    .when(service)
                    .deleteFile(CLIENT, FILE_ID);

            mockMvc.perform(delete("/api/v1/clients/{clientId}/files/{id}", CLIENT, FILE_ID))
                    .andExpect(status().isNotFound());
        }
    }
}
