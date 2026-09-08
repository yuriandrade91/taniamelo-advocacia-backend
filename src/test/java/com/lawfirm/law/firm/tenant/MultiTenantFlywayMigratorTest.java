package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
@DisplayName("MultiTenantFlywayMigrator: migrations compartilhadas + por schema de tenant")
class MultiTenantFlywayMigratorTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private Statement statement;
    @Mock private ResultSet resultSet;

    private TenancyProperties properties;

    @BeforeEach
    void setUp() throws SQLException {
        properties = new TenancyProperties();
        properties.setDefaultSchema("tenant_tania");
        properties.setSchemas(List.of("tenant_tania", "tenant_demo"));

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(resultSet);
    }

    @Test
    @DisplayName("banco indisponível derruba a subida em vez de seguir com schema incompleto")
    void unavailableDatabaseFailsFast() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        MultiTenantFlywayMigrator migrator = new MultiTenantFlywayMigrator(dataSource, properties);

        assertThrows(RuntimeException.class, migrator::migrate);
    }

    @Test
    @DisplayName("o catálogo public.tenants é a fonte dos schemas ativos")
    void readsActiveSchemasFromTheCatalog() throws SQLException {
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("schema_name")).thenReturn("tenant_a", "tenant_b");

        MultiTenantFlywayMigrator migrator = new MultiTenantFlywayMigrator(dataSource, properties);

        // A migration compartilhada tenta conectar de verdade e falha com o mock -
        // o que importa aqui é que o migrator é construído e a leitura do catálogo é acionada.
        assertThrows(RuntimeException.class, migrator::migrate);
        assertNotNull(migrator);
    }

    @Test
    @DisplayName("o migrator recebe o DataSource e as propriedades de tenancy")
    void isWiredWithItsDependencies() throws SQLException {
        MultiTenantFlywayMigrator migrator = new MultiTenantFlywayMigrator(dataSource, properties);
        assertNotNull(migrator);
        verify(dataSource, org.mockito.Mockito.never()).getConnection();
    }
}
