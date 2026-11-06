package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ActiveTenantSchemas: tenants ativos vêm do catálogo, não da configuração")
class ActiveTenantSchemasTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private Statement statement;
    @Mock private ResultSet resultSet;

    private ActiveTenantSchemas activeSchemas;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        activeSchemas = new ActiveTenantSchemas(dataSource);
    }

    /** Cada consulta ao catálogo recebe um ResultSet novo com estas linhas. */
    private void catalogReturns(String... schemas) throws SQLException {
        when(statement.executeQuery(anyString()))
                .thenAnswer(
                        _ -> {
                            ResultSet rs = mock(ResultSet.class);
                            Boolean[] rest = new Boolean[schemas.length];
                            Arrays.fill(rest, true);
                            rest[schemas.length - 1] = false;
                            when(rs.next()).thenReturn(schemas.length > 0, rest);
                            when(rs.getString("schema_name"))
                                    .thenReturn(
                                            schemas[0],
                                            Arrays.copyOfRange(schemas, 1, schemas.length));
                            return rs;
                        });
    }

    @Test
    @DisplayName("tenant que só existe no catálogo é reconhecido")
    void catalogTenantIsKnownEvenIfNotConfigured() throws SQLException {
        catalogReturns("tenant_demo", "tenant_prados");

        assertTrue(activeSchemas.isKnown("tenant_prados"));
        assertEquals(List.of("tenant_demo", "tenant_prados"), activeSchemas.all());
    }

    @Test
    @DisplayName("schema fora do catálogo é recusado, inclusive o configurado e inativo")
    void unknownSchemaIsRejected() throws SQLException {
        catalogReturns("tenant_demo");

        assertFalse(activeSchemas.isKnown("tenant_tania"));
        assertFalse(activeSchemas.isKnown("public"));
    }

    @Test
    @DisplayName("null, caixa errada e tentativa de injeção não passam")
    void rejectsNullWrongCaseAndInjection() throws SQLException {
        catalogReturns("tenant_tania");

        assertFalse(activeSchemas.isKnown(null));
        assertFalse(activeSchemas.isKnown(""));
        assertFalse(activeSchemas.isKnown("TENANT_TANIA"));
        assertFalse(activeSchemas.isKnown("tenant_tania; DROP TABLE clients"));
    }

    @Test
    @DisplayName("schema desconhecido recarrega o catálogo uma vez antes de ser recusado")
    void missReloadsCatalogOnce() throws SQLException {
        catalogReturns("tenant_tania");
        activeSchemas.all();

        assertFalse(activeSchemas.isKnown("tenant_novo"));

        verify(dataSource, times(2)).getConnection();
    }

    @Test
    @DisplayName("tenant cadastrado depois do primeiro carregamento passa a ser reconhecido")
    void tenantAddedAfterCacheIsPickedUp() throws SQLException {
        catalogReturns("tenant_tania");
        activeSchemas.all();
        catalogReturns("tenant_prados", "tenant_tania");

        assertTrue(activeSchemas.isKnown("tenant_prados"));
    }

    @Test
    @DisplayName("catálogo ilegível ou vazio significa nenhum tenant, sem lista de reserva")
    void unreadableOrEmptyCatalogMeansNoTenants() throws SQLException {
        doThrow(new SQLException("sem tabela")).when(dataSource).getConnection();
        assertEquals(List.of(), activeSchemas.all());
        assertFalse(activeSchemas.isKnown("tenant_tania"));

        doReturn(connection).when(dataSource).getConnection();
        when(resultSet.next()).thenReturn(false);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        assertEquals(List.of(), activeSchemas.reload());
    }
}
