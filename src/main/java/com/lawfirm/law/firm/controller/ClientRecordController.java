package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.service.ClientRecordPdf;
import com.lawfirm.law.firm.service.ClientRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cliente - Ficha", description = "Ficha completa do cliente para download")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/record")
public class ClientRecordController {

    private final ClientRecordService service;

    public ClientRecordController(ClientRecordService service) {
        this.service = service;
    }

    @Operation(
            summary = "Baixar a ficha completa do cliente em PDF",
            description =
                    """
                    Dados pessoais, endereços, dados previdenciários, períodos de deficiência, \
                    entrevistas, lista de documentos e simulações, compromissos e históricos.

                    - **Pagamentos** só saem para ADMIN, como em `/clients/{clientId}/payments`.
                    - **A senha do INSS nunca sai**, para papel nenhum: tem rota própria.
                    - Os arquivos anexados são listados, não embutidos; o conteúdo continua em \
                    `/clients/{clientId}/files/{fileId}/download`.

                    Cada download fica registrado na auditoria com quem baixou e quando.""")
    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable UUID clientId) {
        ClientRecordPdf pdf = service.export(clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(pdf.filename(), StandardCharsets.UTF_8)
                        .build());
        headers.setContentType(MediaType.APPLICATION_PDF);
        return ResponseEntity.ok().headers(headers).body(pdf.content());
    }
}
