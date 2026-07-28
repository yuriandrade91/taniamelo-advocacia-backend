package com.lawfirm.law.firm.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Multi-tenancy por SCHEMA: usa um único {@link DataSource}/pool e, a cada conexão entregue ao
 * Hibernate, ajusta o {@code search_path} para o schema do tenant corrente (mais {@code public},
 * onde vive a extensão {@code unaccent}). Ao devolver a conexão ao pool, reseta o {@code
 * search_path} para {@code public} para não vazar o schema de um tenant para a próxima requisição.
 *
 * <p>O identificador do tenant é o nome do schema. É validado contra {@code [a-z0-9_]} antes de
 * entrar no SQL — defesa extra contra injeção, além de o resolver só aceitar schemas configurados.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient DataSource dataSource;
    private final TenancyProperties properties;

    public SchemaMultiTenantConnectionProvider(
            DataSource dataSource, TenancyProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * Usada pelo Hibernate quando não há tenant corrente (ex.: validação do schema no boot). Aponta
     * para o schema default: sem isso, a conexão fica com o {@code search_path} padrão do Postgres
     * ({@code public}), onde nenhuma tabela de tenant existe, e a validação falha para todas elas.
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return withSchema(dataSource.getConnection(), properties.getDefaultSchema());
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        return withSchema(dataSource.getConnection(), tenantIdentifier);
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection)
            throws SQLException {
        resetAndClose(connection);
    }

    private static Connection withSchema(Connection connection, String tenantIdentifier)
            throws SQLException {
        String schema = sanitize(tenantIdentifier);
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO \"" + schema + "\", public");
        }
        return connection;
    }

    private static void resetAndClose(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO public");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return DataSource.class.isAssignableFrom(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (DataSource.class.isAssignableFrom(unwrapType)) {
            return (T) dataSource;
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType.getName());
    }

    private static String sanitize(String schema) {
        if (schema == null || !schema.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Schema de tenant inválido: " + schema);
        }
        return schema;
    }
}
