package com.lawfirm.law.firm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenApiConfig: documentação, esquemas de segurança e customizadores globais")
class OpenApiConfigTest {

    @Mock private TenantRepository tenantRepository;

    private OpenApiConfig config;

    @BeforeEach
    void setUp() {
        TenancyProperties properties = new TenancyProperties();
        properties.setHeaderName("X-Tenant-Id");
        config = new OpenApiConfig(properties, tenantRepository);
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(TestFixtures.tenant()));
    }

    @Test
    @DisplayName("o documento traz título, versão e os dois esquemas de segurança")
    void documentHasInfoAndSecuritySchemes() {
        OpenAPI api = config.lawFirmOpenApi();

        assertEquals("Tania Melo Advocacia - API", api.getInfo().getTitle());
        assertEquals("v1", api.getInfo().getVersion());
        assertNotNull(api.getInfo().getContact());
        assertTrue(api.getInfo().getDescription().contains("X-Tenant-Id"));

        var schemes = api.getComponents().getSecuritySchemes();
        assertEquals(SecurityScheme.Type.HTTP, schemes.get("bearerAuth").getType());
        assertEquals("bearer", schemes.get("bearerAuth").getScheme());
        assertEquals("JWT", schemes.get("bearerAuth").getBearerFormat());
        assertEquals(SecurityScheme.Type.APIKEY, schemes.get("tenantHeader").getType());
        assertEquals(SecurityScheme.In.HEADER, schemes.get("tenantHeader").getIn());
        assertEquals("X-Tenant-Id", schemes.get("tenantHeader").getName());

        assertEquals(1, api.getSecurity().size());
    }

    @Test
    @DisplayName("os slugs dos tenants ativos aparecem na ajuda do header")
    void activeTenantSlugsAreListed() {
        assertTrue(
                config.lawFirmOpenApi()
                        .getComponents()
                        .getSecuritySchemes()
                        .get("tenantHeader")
                        .getDescription()
                        .contains("tania"));
    }

    @Test
    @DisplayName("sem tenants cadastrados, a ajuda diz isso explicitamente")
    void noTenantsIsStated() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo")).thenReturn(List.of());

        assertTrue(
                config.lawFirmOpenApi()
                        .getComponents()
                        .getSecuritySchemes()
                        .get("tenantHeader")
                        .getDescription()
                        .contains("(nenhum cadastrado)"));
    }

    @Test
    @DisplayName("vários tenants ativos são listados separados por vírgula")
    void multipleTenantsAreJoined() {
        Tenant demo = TestFixtures.tenant();
        demo.setSlug("demo");
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(TestFixtures.tenant(), demo));

        String description =
                config.lawFirmOpenApi()
                        .getComponents()
                        .getSecuritySchemes()
                        .get("tenantHeader")
                        .getDescription();

        assertTrue(description.contains("tania, demo"));
    }

    @Test
    @DisplayName("orderedTags reordena as collections na sequência definida e deduplica")
    void orderedTagsSortsAndDeduplicates() {
        GlobalOpenApiCustomizer customizer = config.orderedTags();

        OpenAPI api = new OpenAPI();
        api.setTags(
                List.of(
                        new Tag().name("Cliente - Financeiro"),
                        new Tag().name("Autenticação"),
                        new Tag().name("Tag desconhecida"),
                        new Tag().name("Cliente(s)"),
                        new Tag().name("Autenticação"),
                        new Tag().name("Agenda")));

        customizer.customise(api);

        List<String> names = api.getTags().stream().map(Tag::getName).toList();
        assertEquals(
                List.of(
                        "Autenticação",
                        "Cliente(s)",
                        "Cliente - Financeiro",
                        "Agenda",
                        "Tag desconhecida"),
                names);
    }

    @Test
    @DisplayName("orderedTags mantém tags não listadas ao final, na ordem em que apareceram")
    void unlistedTagsGoLast() {
        OpenAPI api = new OpenAPI();
        api.setTags(List.of(new Tag().name("Zeta"), new Tag().name("Alfa")));

        config.orderedTags().customise(api);

        assertEquals(List.of("Zeta", "Alfa"), api.getTags().stream().map(Tag::getName).toList());
    }

    @Test
    @DisplayName("standardErrorResponses anexa 400/401/404/409/422/500 a todas as operações")
    void standardErrorResponsesAreAttachedEverywhere() {
        Operation get = new Operation().responses(new ApiResponses());
        Operation post = new Operation().responses(new ApiResponses());

        OpenAPI api = new OpenAPI();
        Paths paths = new Paths();
        paths.addPathItem("/api/v1/clients", new PathItem().get(get).post(post));
        api.setPaths(paths);

        config.standardErrorResponses().customise(api);

        for (Operation operation : List.of(get, post)) {
            for (String status : List.of("400", "401", "404", "409", "422", "500")) {
                assertNotNull(operation.getResponses().get(status), "faltou o status " + status);
                assertTrue(
                        operation
                                .getResponses()
                                .get(status)
                                .getDescription()
                                .contains("envelope padrão"),
                        status);
            }
        }
    }

    @Test
    @DisplayName("um documento sem operações não quebra o customizador")
    void emptyPathsAreSafe() {
        OpenAPI api = new OpenAPI();
        api.setPaths(new Paths());

        config.standardErrorResponses().customise(api);

        assertTrue(api.getPaths().isEmpty());
    }
}
