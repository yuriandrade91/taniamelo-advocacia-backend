package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.support.PostgresContainerConfig;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
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
 * <p>O que ele afirma é modesto e suficiente: a V23 aplica sem erro e a massa dos dez meses chega
 * ao banco nos dois schemas. Erro de sintaxe, coluna errada, rótulo de enum inválido ou violação de
 * restrição passam a quebrar aqui, não no deploy.
 */
@SpringBootTest(properties = "app.seed.demo-data=true")
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@DisplayName("Massa de demonstração (V23) aplica e popula os dez meses")
class MassaDemonstracaoSeedTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("os dois schemas recebem carteira, pagamentos, agenda e financeiro")
    void massaAplicadaNosDoisSchemas() throws Exception {
        for (String schema : new String[] {"tenant_tania", "tenant_demo"}) {
            // A carteira é o que o resto depende; se ela não entrou, o guard da V23 pula o resto.
            assertTrue(
                    contar(schema, "clients WHERE cpf LIKE '870.11%'") > 0,
                    "carteira de demonstração ausente em " + schema);

            // Dez parcelas de janeiro a outubro, mais o honorário de êxito em atraso.
            assertEquals(
                    11,
                    contar(schema, "client_payments"),
                    "esperado 10 parcelas (jan-out) + 1 honorário de êxito em " + schema);

            // Dez meses de honorários, mais o parecer avulso e a receita cancelada.
            assertEquals(
                    12,
                    contar(schema, "office_revenues"),
                    "esperado 10 meses + 2 avulsas em " + schema);

            // Quatro despesas recorrentes por mês, dez meses.
            assertEquals(
                    40,
                    contar(schema, "office_expenses"),
                    "esperado 4 categorias x 10 meses em " + schema);

            // Três compromissos por mês, dez meses, mais um cancelado.
            assertEquals(
                    31,
                    contar(schema, "appointments"),
                    "esperado 3 tipos x 10 meses + 1 cancelado em " + schema);

            // A janela pedida é fechada: janeiro a outubro de 2026, nada fora dela.
            assertEquals(
                    0,
                    contar(
                            schema,
                            "office_revenues WHERE due_date < DATE '2026-01-01'"
                                    + " AND description LIKE 'Honorários do mês%'"),
                    "receita mensal antes de jan/2026 em " + schema);
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
