package com.lawfirm.law.firm.tenant;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Schemas dos tenants ativos, lidos do catálogo {@code public.tenants}: é o que torna o cadastro de
 * um escritório uma linha no banco, sem editar {@code app.tenancy.schemas} nem reiniciar.
 *
 * <p>Usa JDBC puro de propósito. O {@code CurrentTenantIdentifierResolver} depende desta classe e é
 * necessário para montar o {@code EntityManagerFactory}; um repositório JPA aqui fecharia um ciclo
 * (resolver → repositório → EntityManagerFactory → resolver).
 *
 * <p>Catálogo ilegível ou vazio significa nenhum tenant conhecido; não há lista de reserva em
 * configuração. Como um schema desconhecido sempre recarrega antes de ser recusado, o cache se
 * recupera sozinho quando o catálogo volta.
 */
@Component
public class ActiveTenantSchemas {

    private static final Logger log = LoggerFactory.getLogger(ActiveTenantSchemas.class);

    private final DataSource dataSource;
    private final AtomicReference<List<String>> cache = new AtomicReference<>();

    public ActiveTenantSchemas(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> all() {
        List<String> cached = cache.get();
        return cached != null ? cached : reload();
    }

    /**
     * Comparação exata (case-sensitive) contra a lista: defesa contra injeção via header/claim. Um
     * schema fora do cache pode ser um tenant cadastrado depois dele, então recarrega uma vez antes
     * de recusar.
     */
    public boolean isKnown(String schema) {
        if (schema == null) {
            return false;
        }
        return all().contains(schema) || reload().contains(schema);
    }

    public List<String> reload() {
        List<String> loaded = load();
        cache.set(loaded);
        return loaded;
    }

    private List<String> load() {
        List<String> schemas = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement st = connection.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT schema_name FROM public.tenants "
                                        + "WHERE status = 'ativo' ORDER BY schema_name")) {
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
        } catch (Exception ex) {
            log.warn("Não foi possível ler public.tenants ({}).", ex.getMessage());
        }
        return List.copyOf(schemas);
    }
}
