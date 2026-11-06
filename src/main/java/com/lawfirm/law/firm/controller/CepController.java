package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.CepLookupResponseDTO;
import com.lawfirm.law.firm.security.CurrentUser;
import com.lawfirm.law.firm.service.CepLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de CEP para preencher o formulário de endereço.
 *
 * <p>Fica sob a mesma {@code @Tag} dos endereços do cliente porque é ali que ela é usada, mas a
 * rota é de primeiro nível e não aninhada em {@code /clients/{clientId}}: resolver um CEP não
 * depende de cliente nenhum, e pendurá-la num cliente obrigaria a tela a ter um cadastro aberto
 * para descobrir uma rua.
 *
 * <p><b>Exige autenticação</b>, como todo o resto da API — cai no {@code
 * anyRequest().authenticated()} do {@code SecurityConfig}. Não é rota aberta: ela gasta cota nossa
 * contra um serviço de terceiro, e aberta seria um proxy de graça para qualquer um na internet.
 */
@Tag(
        name = "Cliente - Endereço(s)",
        description =
                "Endereços residencial/comercial/correspondência do cliente (um marcado como principal)")
@RestController
@RequestMapping("/api/v1/cep")
public class CepController {

    private final CepLookupService service;

    public CepController(CepLookupService service) {
        this.service = service;
    }

    @Operation(
            summary = "Consultar CEP",
            description =
                    """
                    Resolve um CEP e devolve os campos do endereço já com os nomes do cadastro \
                    (`street`, `neighborhood`, `city`, `state`), prontos para preencher o \
                    formulário. Faltam só `addressNumber` e `addressType`, que o CEP não sabe.

                    Aceita `30240-000`, `30240000` ou `30.240-000`. Oito dígitos é o único \
                    requisito; qualquer outra coisa é **400** antes de sair para a rede.

                    **CEP inexistente é 404**, não um endereço em branco. Consulta repetida sai do \
                    cache do servidor e não conta cota. Há limite por usuário — estourar é \
                    **429**. Provedor fora do ar é **500** com `EXTERNAL_SERVICE_ERROR`, sem vazar \
                    detalhe de quem é o provedor.""")
    @GetMapping("/{cep}")
    public ResponseEntity<ApiResponse<CepLookupResponseDTO>> lookup(
            @Parameter(description = "CEP com ou sem pontuação", example = "30240-000")
                    @PathVariable
                    String cep) {
        UUID usuario = CurrentUser.id();
        return ResponseEntity.ok(
                ApiResponse.successObject(
                        service.buscar(cep, usuario == null ? null : usuario.toString())));
    }
}
