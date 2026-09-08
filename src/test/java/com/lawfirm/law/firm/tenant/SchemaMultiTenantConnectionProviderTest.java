package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchemaMultiTenantConnectionProvider: search_path por tenant no pool compartilhado")
class SchemaMultiTenantConnectionProviderTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private Statement statement;

    private SchemaMultiTenantConnectionProvider provider;

    @BeforeEach
    void setUp() throws SQLException {
        TenancyProperties properties = new TenancyProperties();
        properties.setDefaultSchema("tenant_tania");
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));
        provider = new SchemaMultiTenantConnectionProvider(dataSource, properties);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
    }

    private String executedSql() throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement).execute(sql.capture());
        return sql.getValue();
    }

    @Test
    @DisplayName("getConnection aponta o search_path para o schema do tenant, mais public")
    void setsSearchPathForTenant() throws SQLException {
        assertSame(connection, provider.getConnection("tenant_demo"));
        assertEquals("SET search_path TO \"tenant_demo\", public", executedSql());
    }

    @Test
    @DisplayName("getAnyConnection usa o schema default (boot e validação de schema)")
    void anyConnectionUsesDefaultSchema() throws SQLException {
        assertSame(connection, provider.getAnyConnection());
        assertEquals("SET search_path TO \"tenant_tania\", public", executedSql());
    }

    @Test
    @DisplayName("devolver a conexão reseta o search_path e fecha")
    void releaseResetsSearchPathAndCloses() throws SQLException {
        provider.releaseConnection("tenant_demo", connection);

        assertEquals("SET search_path TO public", executedSql());
        verify(connection).close();
    }

    @Test
    @DisplayName("releaseAnyConnection também reseta e fecha")
    void releaseAnyResetsAndCloses() throws SQLException {
        provider.releaseAnyConnection(connection);

        assertEquals("SET search_path TO public", executedSql());
        verify(connection).close();
    }

    @Test
    @DisplayName("a conexão é fechada mesmo se o reset do search_path falhar")
    void closesEvenWhenResetFails() throws SQLException {
        when(statement.execute("SET search_path TO public")).thenThrow(new SQLException("caiu"));

        assertThrows(SQLException.class, () -> provider.releaseAnyConnection(connection));
        verify(connection).close();
    }

    @ParameterizedTest(name = "schema inválido \"{0}\" é rejeitado antes de virar SQL")
    @ValueSource(
            strings = {
                "tenant_tania; DROP SCHEMA public CASCADE",
                "TENANT_TANIA",
                "tenant-tania",
                "tenant tania",
                "\"tenant_tania\"",
                "",
                "public.clients"
            })
    void rejectsInvalidSchemaNames(String schema) {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> provider.getConnection(schema));
        assertTrue(ex.getMessage().contains("Schema de tenant inválido"));
    }

    @Test
    @DisplayName("schema null é rejeitado")
    void rejectsNullSchema() {
        assertThrows(IllegalArgumentException.class, () -> provider.getConnection(null));
    }

    @Test
    @DisplayName("não usa aggressive release (a conexão fica presa à transação)")
    void doesNotSupportAggressiveRelease() {
        assertFalse(provider.supportsAggressiveRelease());
    }

    @Test
    @DisplayName("unwrap devolve o DataSource subjacente")
    void unwrapsToDataSource() {
        assertTrue(provider.isUnwrappableAs(DataSource.class));
        assertSame(dataSource, provider.unwrap(DataSource.class));
    }

    @Test
    @DisplayName("unwrap para um tipo não suportado estoura com mensagem clara")
    void unwrapToUnsupportedTypeThrows() {
        assertFalse(provider.isUnwrappableAs(String.class));
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> provider.unwrap(String.class));
        assertTrue(ex.getMessage().contains("String"));
    }
}
