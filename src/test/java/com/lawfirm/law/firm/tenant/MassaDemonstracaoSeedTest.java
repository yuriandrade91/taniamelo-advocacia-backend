package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.support.PostgresContainerConfig;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * A migration de massa de demonstração (V23) realmente executa.
 *
 * <p><b>Por que este teste existe, e o que custou não existir.</b> A suíte roda com {@code
 * app.seed.demo-data=false} - necessário, porque a massa no banco de teste colide com os CPFs dos
 * próprios testes. O efeito colateral é que o corpo da V23 não era exercitado por ninguém: ela foi
 * para produção sem nunca ter sido executada em CI e derrubou o deploy, com violação de {@code
 * ux_clients_cpf_active} porque um dos CPFs escolhidos já existia no schema do escritório.
 *
 * <p>Este teste fecha essa lacuna ligando o seed no seu próprio contexto. Não herda de {@link
 * com.lawfirm.law.firm.support.PostgresIntegrationTest} de propósito: a propriedade diferente já
 * cria um contexto (e um container) separado, e é isso que mantém a massa fora do banco dos demais
 * testes.
 *
 * <p>O que ele afirma é modesto e suficiente: a cadeia de seed (V23, depois substituída pela massa
 * completa da V29) aplica sem erro e chega ao banco nos dois schemas com o volume dos seeds locais.
 * Erro de sintaxe, coluna errada, rótulo de enum inválido ou violação de restrição passam a quebrar
 * aqui, não no deploy.
 */
@SpringBootTest(properties = "app.seed.demo-data=true")
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@DisplayName("Massa de demonstração (V23/V29) aplica e popula os dois schemas")
class MassaDemonstracaoSeedTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("os dois schemas recebem carteira, pagamentos, agenda e financeiro")
    void massaAplicadaNosDoisSchemas() throws Exception {
        // Estado final da cadeia de seed: a V29 trunca e resemeia com a massa de db/mock-data,
        // então os números são os dos seeds locais (seed_tenant_tania.sql, seed_tenant_demo.sql).
        Map<String, long[]> esperado =
                Map.of(
                        // usuários, clientes, pagamentos, receitas, despesas, agenda, histórico
                        "tenant_tania", new long[] {2, 125, 78, 53, 119, 7, 3},
                        "tenant_demo", new long[] {2, 6, 3, 5, 7, 4, 2});
        String[] tabelas = {
            "users",
            "clients",
            "client_payments",
            "office_revenues",
            "office_expenses",
            "appointments",
            "appointment_history"
        };

        for (String schema : new String[] {"tenant_tania", "tenant_demo"}) {
            long[] valores = esperado.get(schema);
            for (int i = 0; i < tabelas.length; i++) {
                assertEquals(
                        valores[i],
                        contar(schema, tabelas[i]),
                        tabelas[i] + " fora do esperado pela massa local em " + schema);
            }

            // A massa enxuta da V23 não pode sobrar: a V28 deu a um cliente dela o mesmo CPF da
            // Maria do seed_mock, e conviver com ela é o que quebraria ux_clients_cpf_active.
            assertEquals(
                    0,
                    contar(schema, "clients WHERE id::text LIKE 'd0000023%'"),
                    "massa da V23 sobrou em " + schema);

            // Etapa do INSS preenchida na massa, mas não em todos: cliente sem requerimento
            // protocolado fica nulo de propósito, e uma massa em que todo mundo tem etapa
            // esconderia justamente o estado mais comum da base real.
            assertTrue(
                    contar(schema, "clients WHERE request_stage IS NOT NULL") > 0,
                    "nenhum cliente com etapa do INSS em " + schema);
            assertTrue(
                    contar(schema, "clients WHERE request_stage IS NULL") > 0,
                    "esperado ao menos um cliente sem requerimento protocolado em " + schema);
        }
    }

    @Test
    @DisplayName("reexecutar migrate não duplica migrations já aplicadas")
    void reaplicarMigrateNaoDuplica() throws Exception {
        for (String schema : new String[] {"tenant_tania", "tenant_demo"}) {
            long pagamentosAntes = contar(schema, "client_payments");
            long receitasAntes = contar(schema, "office_revenues");
            long despesasAntes = contar(schema, "office_expenses");
            long agendaAntes = contar(schema, "appointments");
            long clientesAntes = contar(schema, "clients");

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration/tenant")
                    .placeholders(Map.of("seedDemoData", "true"))
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();

            assertEquals(
                    pagamentosAntes,
                    contar(schema, "client_payments"),
                    "pagamentos duplicados em " + schema);
            assertEquals(
                    receitasAntes,
                    contar(schema, "office_revenues"),
                    "receitas duplicadas em " + schema);
            assertEquals(
                    despesasAntes,
                    contar(schema, "office_expenses"),
                    "despesas duplicadas em " + schema);
            assertEquals(
                    agendaAntes, contar(schema, "appointments"), "agenda duplicada em " + schema);
            assertEquals(
                    clientesAntes, contar(schema, "clients"), "clientes duplicados em " + schema);
        }
    }

    private long contar(String schema, String de) throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            st.execute("SET search_path TO " + schema + ", public");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + de)) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
