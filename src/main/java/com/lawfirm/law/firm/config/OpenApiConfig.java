package com.lawfirm.law.firm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI. Além dos metadados, um customizer global adiciona a TODAS as operações as
 * respostas de erro padrão da central de erros (GlobalExceptionHandler), para o contrato de erro
 * aparecer no Swagger sem repetir @ApiResponse em cada endpoint.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI lawFirmOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Tania Melo Advocacia - API")
                                .description(
                                        """
                                API do sistema de gestão do escritório previdenciário.

                                **Convenções:**
                                - Toda resposta usa o envelope `{ success, data, pagination?, errors? }`.
                                - Listagens são paginadas com `pageNumber` (1-based) e `pageSize`; \
                                o bloco `pagination` retorna total de registros/páginas.
                                - Enums aceitam o nome da constante ou o label PT-BR \
                                (ex.: `APOSENTADORIA_RURAL` ou `Aposentadoria rural`).
                                - Erros seguem a central de tratamento: 400 validação, 401/403 \
                                autenticação/autorização, 404 não encontrado, 409 duplicidade, \
                                422 regra de negócio, 500 erro de sistema (sem detalhe técnico).
                                - Autentique-se em `POST /api/v1/auth/login` e envie o JWT como \
                                `Authorization: Bearer <token>`.
                                """)
                                .version("v1")
                                .contact(new Contact().name("Equipe de desenvolvimento")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .name(BEARER_SCHEME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "JWT obtido em POST /api/v1/auth/login")));
    }

    /**
     * Ordem das collections no Swagger UI: auth -> cliente/clientes -> dados pessoais -> endereços
     * -> dados profissionais -> entrevista -> arquivos -> situação -> financeiro (em vez da ordem
     * alfabética/de descoberta padrão do springdoc). Personal-data, professional-data e
     * situation-history vivem no mesmo ClientController mas são movidos para suas próprias tags via
     * {@code @Operation(tags = ...)} por operação, sobrescrevendo a tag de classe só nesses
     * métodos. Roda depois da descoberta automática de tags via @Tag/@Operation nos controllers,
     * então dedupe por nome e reordena pela lista fixa abaixo; tags não listadas ficam ao final, na
     * ordem em que apareceram.
     */
    @Bean
    public GlobalOpenApiCustomizer orderedTags() {
        List<String> order =
                List.of(
                        "Autenticação",
                        "Cliente(s)",
                        "Cliente - Dados Pessoais",
                        "Cliente - Endereço(s)",
                        "Cliente - Dados Profissionais",
                        "Cliente - Entrevista",
                        "Cliente - Arquivos",
                        "Cliente - Situação",
                        "Cliente - Financeiro");
        return openApi -> {
            Map<String, Tag> byName = new LinkedHashMap<>();
            for (Tag tag : openApi.getTags()) {
                byName.putIfAbsent(tag.getName(), tag);
            }
            List<Tag> ordered = new ArrayList<>();
            for (String name : order) {
                Tag tag = byName.remove(name);
                if (tag != null) {
                    ordered.add(tag);
                }
            }
            ordered.addAll(byName.values());
            openApi.setTags(ordered);
        };
    }

    /** Anexa as respostas de erro padrão a todas as operações documentadas. */
    @Bean
    public GlobalOpenApiCustomizer standardErrorResponses() {
        return openApi ->
                openApi.getPaths()
                        .values()
                        .forEach(
                                pathItem ->
                                        pathItem.readOperations()
                                                .forEach(
                                                        operation -> {
                                                            var responses =
                                                                    operation.getResponses();
                                                            responses.addApiResponse(
                                                                    "400",
                                                                    envelope(
                                                                            "Erro de validação (campo inválido ou requisição malformada)"));
                                                            responses.addApiResponse(
                                                                    "401",
                                                                    envelope(
                                                                            "Não autenticado (token ausente, inválido ou expirado)"));
                                                            responses.addApiResponse(
                                                                    "404",
                                                                    envelope(
                                                                            "Recurso não encontrado"));
                                                            responses.addApiResponse(
                                                                    "409",
                                                                    envelope(
                                                                            "Conflito - valor duplicado para campo único"));
                                                            responses.addApiResponse(
                                                                    "422",
                                                                    envelope(
                                                                            "Regra de negócio violada"));
                                                            responses.addApiResponse(
                                                                    "500",
                                                                    envelope(
                                                                            "Erro interno de sistema (sem detalhe técnico no corpo)"));
                                                        }));
    }

    private static ApiResponse envelope(String description) {
        return new ApiResponse()
                .description(
                        description
                                + " - envelope padrão { success: false, errors: [{ field, message, code }] }");
    }
}
