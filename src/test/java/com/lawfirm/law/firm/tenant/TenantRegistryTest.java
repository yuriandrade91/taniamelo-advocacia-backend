package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantRegistry: tradução do identificador público (UUID/slug) para o schema físico")
class TenantRegistryTest {

    @Mock private TenantRepository tenantRepository;

    @InjectMocks private TenantRegistry registry;

    private Tenant tenant() {
        return TestFixtures.tenant();
    }

    @Test
    @DisplayName("resolve pelo UUID e pelo slug")
    void resolvesByUuidAndSlug() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(tenant()));

        assertEquals(
                "tenant_tania",
                registry.schemaFor(TestFixtures.TENANT_ID.toString()).orElseThrow());
        assertEquals("tenant_tania", registry.schemaFor("tania").orElseThrow());
    }

    @Test
    @DisplayName("espaços em volta do identificador são ignorados")
    void trimsIdentifier() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(tenant()));

        assertEquals("tenant_tania", registry.schemaFor("  tania  ").orElseThrow());
    }

    @Test
    @DisplayName("identificador nulo, vazio ou em branco não consulta o repositório")
    void blankIdentifierShortCircuits() {
        assertTrue(registry.schemaFor(null).isEmpty());
        assertTrue(registry.schemaFor("").isEmpty());
        assertTrue(registry.schemaFor("   ").isEmpty());
        verify(tenantRepository, times(0)).findByStatusOrderByRazaoSocialAsc("ativo");
    }

    @Test
    @DisplayName("identificador desconhecido devolve vazio")
    void unknownIdentifierIsEmpty() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(tenant()));

        assertTrue(registry.schemaFor("escritorio-que-nao-existe").isEmpty());
    }

    @Test
    @DisplayName("o nome do schema físico NÃO é aceito como identificador público")
    void physicalSchemaIsNotAPublicIdentifier() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(tenant()));

        assertTrue(registry.schemaFor("tenant_tania").isEmpty());
    }

    @Test
    @DisplayName("o catálogo é carregado uma única vez e mantido em cache")
    void catalogIsCachedAfterFirstLoad() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(tenant()));

        registry.schemaFor("tania");
        registry.schemaFor("tania");
        registry.schemaFor(TestFixtures.TENANT_ID.toString());

        verify(tenantRepository, times(1)).findByStatusOrderByRazaoSocialAsc("ativo");
    }

    @Test
    @DisplayName("reload recarrega o catálogo - novo escritório passa a ser reconhecido")
    void reloadRefreshesTheCatalog() {
        Tenant novo = TestFixtures.tenant();
        novo.setSlug("novo");
        novo.setSchemaName("tenant_novo");

        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo"))
                .thenReturn(List.of(tenant()))
                .thenReturn(List.of(tenant(), novo));

        assertTrue(registry.schemaFor("novo").isEmpty());
        registry.reload();
        assertEquals("tenant_novo", registry.schemaFor("novo").orElseThrow());
        verify(tenantRepository, times(2)).findByStatusOrderByRazaoSocialAsc("ativo");
    }

    @Test
    @DisplayName("só tenants ativos entram no catálogo")
    void onlyActiveTenantsAreLoaded() {
        when(tenantRepository.findByStatusOrderByRazaoSocialAsc("ativo")).thenReturn(List.of());

        assertFalse(registry.schemaFor("tania").isPresent());
        verify(tenantRepository).findByStatusOrderByRazaoSocialAsc("ativo");
    }
}
