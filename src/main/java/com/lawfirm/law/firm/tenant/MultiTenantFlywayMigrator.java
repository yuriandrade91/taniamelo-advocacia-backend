package com.lawfirm.law.firm.tenant;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Roda as migrations Flyway manualmente (a auto-config do Spring Boot fica desligada via {@code
 * spring.flyway.enabled=false}), em duas etapas:
 *
 * <ol>
 *   <li><b>Compartilhada</b> — {@code db/migration/shared} no schema {@code public} (extensão
 *       {@code unaccent}, que é objeto de banco, não por-schema).
 *   <li><b>Por tenant</b> — {@code db/migration/tenant} em cada schema configurado (cria o schema
 *       se não existir e mantém um {@code flyway_schema_history} próprio por tenant).
 * </ol>
 *
 * Executa no {@code @PostConstruct}; o {@code EntityManagerFactory} depende deste bean (ver {@link
 * MultiTenantJpaConfig}), então as migrations terminam antes de o Hibernate validar o schema.
 */
@Component
public class MultiTenantFlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(MultiTenantFlywayMigrator.class);

    private final DataSource dataSource;
    private final TenancyProperties properties;
    private final boolean seedDemoData;

    public MultiTenantFlywayMigrator(
            DataSource dataSource,
            TenancyProperties properties,
            @Value("${app.seed.demo-data:true}") boolean seedDemoData) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.seedDemoData = seedDemoData;
    }

    @PostConstruct
    public void migrate() {
        // 1. Compartilhada (public): extensões.
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration/shared")
                .baselineOnMigrate(true)
                .load()
                .migrate();
        log.info("Flyway: migrations compartilhadas aplicadas (schema public).");

        // 2. Por tenant: o catálogo public.tenants (semeado na migration compartilhada) é a fonte
        // de verdade dos schemas ativos; a lista em app.tenancy.schemas é só fallback.
        List<String> schemas = activeTenantSchemas();
        for (String schema : schemas) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration/tenant")
                    // ATENÇÃO: este placeholder é OBRIGATÓRIO para quem aponta para
                    // db/migration/tenant. Sem ele o Flyway recusa a V23 com "No value
                    // provided for placeholder" - foi o que quebrou o
                    // MigracaoSobreDadoLegadoIntegrationTest, que configura o próprio Flyway.
                    // Ao criar outra configuração para essa location, informe-o.
                    //
                    // Liga/desliga as migrations que semeiam massa de demonstração (V23). A
                    // suíte roda com false: a massa entrando no banco de teste faria os testes
                    // que afirmam contagem exata dependerem dela - e o primeiro efeito foi
                    // colisão de CPF com a massa do próprio teste.
                    .placeholders(Map.of("seedDemoData", String.valueOf(seedDemoData)))
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            log.info("Flyway: migrations de tenant aplicadas (schema {}).", schema);
        }
    }

    /**
     * Lê os schemas de tenants ativos do catálogo {@code public.tenants}. Se a tabela ainda não
     * existir ou vier vazia (primeira subida antes do seed, ou erro), cai na lista configurada em
     * {@code app.tenancy.schemas}.
     */
    private List<String> activeTenantSchemas() {
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
            log.warn(
                    "Não foi possível ler public.tenants ({}); usando app.tenancy.schemas como"
                            + " fallback.",
                    ex.getMessage());
        }
        if (schemas.isEmpty()) {
            return properties.getSchemas();
        }
        return schemas;
    }
}
