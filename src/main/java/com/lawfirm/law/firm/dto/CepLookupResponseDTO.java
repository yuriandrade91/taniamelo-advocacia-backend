package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O endereço que um CEP resolve, pronto para preencher o formulário.
 *
 * <p>Os nomes dos campos são os do {@link ClientAddressRequestDTO} de propósito — {@code street},
 * {@code neighborhood}, {@code city}, {@code state} — e não os do provedor de CEP, que usa {@code
 * logradouro}, {@code bairro}, {@code localidade} e {@code uf}. A tela copia campo a campo sem
 * tradução, e trocar de provedor um dia não mexe no contrato nem no front.
 *
 * <p>Não vem {@code addressNumber} nem {@code addressType}: CEP não sabe o número da casa, e o tipo
 * (residencial, comercial) é escolha de quem cadastra. São exatamente os dois campos que sobram
 * para a pessoa preencher.
 */
public record CepLookupResponseDTO(
        @Schema(description = "CEP no mesmo formato em que é gravado", example = "30240-000")
                String zipCode,
        @Schema(example = "Rua dos Timbiras") String street,
        @Schema(
                        description =
                                "Complemento que o próprio CEP carrega (ex.: \"lado ímpar\"), não o"
                                        + " do cliente",
                        example = "de 1001 ao fim - lado ímpar")
                String complement,
        @Schema(example = "Funcionários") String neighborhood,
        @Schema(example = "Belo Horizonte") String city,
        @Schema(description = "Sigla de 2 letras", example = "MG") String state) {}
