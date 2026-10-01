package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ClientDisabilityPeriodRequestDTO;
import com.lawfirm.law.firm.dto.ClientDisabilityPeriodResponseDTO;
import com.lawfirm.law.firm.dto.DisabilityConversionDTO;
import com.lawfirm.law.firm.model.DisabilityGrade;
import com.lawfirm.law.firm.security.RequerAdvogado;
import com.lawfirm.law.firm.service.ClientDisabilityPeriodService;
import com.lawfirm.law.firm.service.RequestEnums;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Cliente - Períodos de deficiência",
        description =
                "Intervalos em que a deficiência foi reconhecida, por grau, e a conversão de tempo da LC 142/2013")
@RestController
@RequestMapping("/api/v1/clients/{clientId}/disability-periods")
public class ClientDisabilityPeriodController {

    private final ClientDisabilityPeriodService service;

    public ClientDisabilityPeriodController(ClientDisabilityPeriodService service) {
        this.service = service;
    }

    @Operation(
            summary = "Cadastrar intervalo de deficiência",
            description =
                    """
                    Grau (`Leve`, `Moderada` ou `Grave`), data de início e, **opcionalmente**, data \
                    de cessação.

                    Omitir `endedOn` é a resposta afirmativa de "esta deficiência se mantém até a \
                    presente data" — não é campo esquecido. Só pode haver **um** intervalo em \
                    aberto por cliente: dois seriam dois graus vigentes hoje, e a conversão não \
                    teria entre o que escolher.

                    Intervalos não podem se sobrepor. O erro nomeia o intervalo conflitante.""")
    @PostMapping
    public ResponseEntity<ApiResponse<ClientDisabilityPeriodResponseDTO>> create(
            @PathVariable UUID clientId, @Valid @RequestBody ClientDisabilityPeriodRequestDTO dto) {
        var created = service.create(clientId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successObject(created));
    }

    @Operation(
            summary = "Listar os intervalos do cliente",
            description =
                    """
                    Do mais antigo para o mais recente, com `days` já calculado (o fim de um \
                    intervalo em aberto é hoje no fuso do escritório).

                    **Sem paginação, de propósito.** O cálculo da LC 142/2013 soma a carreira \
                    inteira — uma página seria um total errado com cara de certo.""")
    @GetMapping
    public ResponseEntity<ApiResponse<ClientDisabilityPeriodResponseDTO>> list(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.successList(service.list(clientId)));
    }

    @Operation(
            summary = "Tempo convertido para uma base (LC 142/2013)",
            description =
                    """
                    Converte o tempo de cada intervalo pelo **seu** grau e soma. Cada intervalo \
                    volta com o próprio fator e o próprio resultado, e o total é a soma do que \
                    está visível — o número vai para dentro de um requerimento, e quem o defende \
                    precisa poder mostrar de onde saiu.

                    `to` é a base de destino; o padrão é `Sem deficiência`, a regra geral (30 anos \
                    para a mulher, 35 para o homem).

                    O fator é o tempo exigido no destino dividido pelo exigido na origem (art. 3º \
                    da LC 142/2013). Devolve **400** se o sexo do cliente não for masculino ou \
                    feminino: a lei tem duas colunas, e escolher uma por conta própria seria uma \
                    decisão jurídica tomada em silêncio.""")
    @GetMapping("/conversion")
    public ResponseEntity<ApiResponse<DisabilityConversionDTO>> conversion(
            @PathVariable UUID clientId, @RequestParam(required = false) String to) {
        DisabilityGrade target = RequestEnums.single("to", to, DisabilityGrade::fromLabel);
        return ResponseEntity.ok(ApiResponse.successObject(service.conversion(clientId, target)));
    }

    @Operation(summary = "Detalhe de um intervalo")
    @GetMapping("/{periodId}")
    public ResponseEntity<ApiResponse<ClientDisabilityPeriodResponseDTO>> get(
            @PathVariable UUID clientId, @PathVariable UUID periodId) {
        return ResponseEntity.ok(ApiResponse.successObject(service.get(clientId, periodId)));
    }

    @Operation(
            summary = "Atualizar intervalo (substituição completa)",
            description =
                    "As mesmas regras do cadastro valem aqui, ignorando o próprio intervalo na "
                            + "verificação de sobreposição.")
    @PutMapping("/{periodId}")
    public ResponseEntity<ApiResponse<ClientDisabilityPeriodResponseDTO>> update(
            @PathVariable UUID clientId,
            @PathVariable UUID periodId,
            @Valid @RequestBody ClientDisabilityPeriodRequestDTO dto) {
        return ResponseEntity.ok(
                ApiResponse.successObject(service.update(clientId, periodId, dto)));
    }

    @Operation(
            summary = "Excluir intervalo",
            description =
                    "Exclusão física: o intervalo é um fato datado, não um registro com histórico "
                            + "próprio, e manter um intervalo \"excluído\" na tabela obrigaria todo "
                            + "cálculo a filtrá-lo — um esquecimento alteraria o tempo do cliente.")
    @RequerAdvogado
    @DeleteMapping("/{periodId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID periodId) {
        service.delete(clientId, periodId);
        return ResponseEntity.noContent().build();
    }
}
