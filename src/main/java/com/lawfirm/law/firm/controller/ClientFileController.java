package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientFileDocumentResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientFileDocumentUploadMetadataDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationResponseDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationUpdateRequestDTO;
import com.lawfirm.law.firm.dto.ClientFileSimulationUploadMetadataDTO;
import com.lawfirm.law.firm.dto.Pagination;
import com.lawfirm.law.firm.security.RequerAdvogado;
import com.lawfirm.law.firm.service.ClientFileService;
import com.lawfirm.law.firm.storage.FileDownload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Collection única de arquivos do cliente, dividida em duas sub-coleções que espelham as abas da
 * tela: /files/documents e /files/simulations (upload/listagem/detalhe/patch, que têm forma
 * diferente por tipo). Download e exclusão não têm forma específica por tipo, então usam um único
 * par de endpoints kind-agnostic: /files/{fileId}/download e DELETE /files/{fileId}.
 */
@Tag(
        name = "Cliente - Arquivos",
        description =
                "Documentos (11 tipos) e simulações do cliente - upload, listagem paginada, download, "
                        + "edição de metadados e exclusão (soft delete)")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/files")
public class ClientFileController {

    private final ClientFileService service;

    public ClientFileController(ClientFileService service) {
        this.service = service;
    }

    // ── Documentos ──

    @Operation(
            summary = "Upload em lote de documentos",
            description =
                    "Recebe N arquivos (PDF/PNG/JPEG) na parte 'files' e uma lista de metadados na mesma "
                            + "ordem na parte 'metadata' (um objeto {documentType, notes} por arquivo). "
                            + "documentType aceita o nome ou o label de um dos 11 tipos.")
    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ClientFileDocumentResponseDTO>> uploadDocuments(
            @PathVariable UUID clientId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("metadata") @Valid List<ClientFileDocumentUploadMetadataDTO> metadata) {
        List<ClientFileDocumentResponseDTO> created =
                service.uploadDocuments(clientId, files, metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successList(created));
    }

    @Operation(
            summary = "Listar documentos",
            description =
                    "Documentos ativos do cliente, mais recente primeiro, paginados no envelope padrão. "
                            + "Filtro opcional por tipo de documento.")
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<ClientFileDocumentResponseDTO>> listDocuments(
            @PathVariable UUID clientId,
            @Parameter(description = "Número da página (1-based)") @RequestParam(defaultValue = "1")
                    int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10")
                    int pageSize,
            @Parameter(description = "Filtra por um dos 11 tipos de documento (nome ou label)")
                    @RequestParam(required = false)
                    String documentType) {
        Page<ClientFileDocumentResponseDTO> page =
                service.listDocuments(clientId, documentType, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de um documento")
    @GetMapping("/documents/{fileId}")
    public ResponseEntity<ApiResponse<ClientFileDocumentResponseDTO>> getDocument(
            @PathVariable UUID clientId, @PathVariable UUID fileId) {
        return ResponseEntity.ok(ApiResponse.successObject(service.getDocument(clientId, fileId)));
    }

    @Operation(
            summary = "Atualizar metadados de um documento",
            description =
                    "Atualização parcial: permite trocar o tipo do documento e/ou as observações. "
                            + "O arquivo em si não muda - para substituir o arquivo, exclua e envie de novo.")
    @PatchMapping("/documents/{fileId}")
    public ResponseEntity<ApiResponse<ClientFileDocumentResponseDTO>> updateDocument(
            @PathVariable UUID clientId,
            @PathVariable UUID fileId,
            @Valid @RequestBody ClientFileDocumentUpdateRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.updateDocument(clientId, fileId, dto)));
    }

    // ── Simulações ──

    @Operation(
            summary = "Upload em lote de simulações",
            description =
                    "Recebe N arquivos PDF na parte 'files' e uma lista de metadados na mesma ordem na "
                            + "parte 'metadata' (um objeto {simulationDate, version, vinculos, notes} por arquivo). "
                            + "A simulação mais recente enviada vira a principal automaticamente.")
    @PostMapping(path = "/simulations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ClientFileSimulationResponseDTO>> uploadSimulations(
            @PathVariable UUID clientId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("metadata") @Valid List<ClientFileSimulationUploadMetadataDTO> metadata) {
        List<ClientFileSimulationResponseDTO> created =
                service.uploadSimulations(clientId, files, metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successList(created));
    }

    @Operation(
            summary = "Listar simulações",
            description =
                    "Simulações ativas do cliente, mais recente primeiro, paginadas no envelope padrão.")
    @GetMapping("/simulations")
    public ResponseEntity<ApiResponse<ClientFileSimulationResponseDTO>> listSimulations(
            @PathVariable UUID clientId,
            @Parameter(description = "Número da página (1-based)") @RequestParam(defaultValue = "1")
                    int pageNumber,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "10")
                    int pageSize) {
        Page<ClientFileSimulationResponseDTO> page =
                service.listSimulations(clientId, pageNumber, pageSize);
        return ResponseEntity.ok(ApiResponse.successList(page.getContent(), Pagination.of(page)));
    }

    @Operation(summary = "Detalhe de uma simulação")
    @GetMapping("/simulations/{fileId}")
    public ResponseEntity<ApiResponse<ClientFileSimulationResponseDTO>> getSimulation(
            @PathVariable UUID clientId, @PathVariable UUID fileId) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.getSimulation(clientId, fileId)));
    }

    @Operation(
            summary = "Atualizar metadados de uma simulação",
            description =
                    "Atualização parcial de data da simulação, versão (texto livre, ex.: v1.0.2), "
                            + "vínculos e observações. Para trocar a principal use o endpoint /principal.")
    @PatchMapping("/simulations/{fileId}")
    public ResponseEntity<ApiResponse<ClientFileSimulationResponseDTO>> updateSimulation(
            @PathVariable UUID clientId,
            @PathVariable UUID fileId,
            @Valid @RequestBody ClientFileSimulationUpdateRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.updateSimulation(clientId, fileId, dto)));
    }

    @Operation(
            summary = "Marcar simulação como principal",
            description =
                    "Marca esta simulação como a principal do cliente, desmarcando a anterior.")
    @PatchMapping("/simulations/{fileId}/principal")
    public ResponseEntity<ApiResponse<ClientFileSimulationResponseDTO>> markPrincipal(
            @PathVariable UUID clientId, @PathVariable UUID fileId) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.markSimulationPrincipal(clientId, fileId)));
    }

    // ── Documento ou simulação (a operação não muda por tipo) ──

    @Operation(
            summary = "Download de um arquivo",
            description =
                    "Funciona tanto para documento quanto para simulação - o tipo é resolvido a "
                            + "partir do próprio id, então um endpoint só cobre os dois.")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID clientId, @PathVariable UUID fileId) {
        return toDownloadResponse(service.downloadFile(clientId, fileId));
    }

    @Operation(
            summary = "Excluir um arquivo",
            description =
                    "Soft delete - o arquivo é preservado como evidência. Funciona tanto para "
                            + "documento quanto para simulação; se a simulação excluída era a "
                            + "principal, a mais recente restante é promovida.")
    @RequerAdvogado
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID fileId) {
        service.deleteFile(clientId, fileId);
        return ResponseEntity.noContent().build();
    }

    // ── Private helpers ──

    private static ResponseEntity<Resource> toDownloadResponse(FileDownload download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(
                                download.originalFilename() != null
                                        ? download.originalFilename()
                                        : "arquivo")
                        .build());
        headers.setContentType(MediaType.parseMediaType(download.mimeType()));
        headers.setContentLength(download.sizeBytes());
        return new ResponseEntity<>(download.resource(), headers, HttpStatus.OK);
    }
}
