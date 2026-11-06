package com.lawfirm.law.firm.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * As migrations aplicadas sobre um banco que JÁ TEM DADO - o caminho que o banco real vai
 * percorrer.
 *
 * <p>A suíte migra sempre um banco vazio (Testcontainers sobe limpo), e num banco vazio a V15, a
 * V16 e a V17 não fazem nada: elas existem para deduplicar CPF, converter tempo de contribuição em
 * texto e renomear benefício aposentado. Ou seja, o CI passava verde sem nunca executar a parte que
 * importa delas - e a primeira vez que esse código rodaria de verdade seria no banco do escritório.
 *
 * <p>Este teste monta o schema até a V14, enfia dado no formato ANTIGO e só então aplica o resto.
 * Foi assim, à mão, que apareceu o defeito da V15: normalizar o CPF com o índice único velho ainda
 * de pé fazia duas linhas distintas ("39053344705" e "390.533.447-05") virarem a mesma NO MEIO do
 * UPDATE, e o índice recusava a própria migration criada para arrumá-las. Num banco vazio isso
 * nunca aconteceria.
 */
@DisplayName("Migrations sobre banco COM dado legado - o caminho da produção")
class MigracaoSobreDadoLegadoIntegrationTest extends PostgresIntegrationTest {

    private static final String SCHEMA = "tenant_legado_teste";

    @Autowired private DataSource dataSource;

    @AfterEach
    void limpar() throws Exception {
        executar("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("dado no formato antigo sobrevive à V15, V16 e V17 - e deixa rastro do que mudou")
    void migraDadoLegado() throws Exception {
        migrarAte("14");
        inserirDadoLegado();

        // O momento da verdade: se qualquer uma das três quebrar sobre dado real, é aqui.
        migrarAte(null);

        conferirV15();
        conferirV16();
        conferirV17();
    }

    /** Duas pessoas com o MESMO CPF em formatos diferentes, texto livre e benefício aposentado. */
    private void inserirDadoLegado() throws Exception {
        inserirCliente(
                "Maria Antiga",
                "39053344705",
                "3 anos, 10 meses, 22 dias",
                "Aposentadoria por invalidez");
        // Mesmo CPF, escrito com pontuação - o cadastro duplicado que a V15 existe para resolver.
        inserirCliente(
                "Maria Duplicada", "390.533.447-05", "nao informado", "Aposentadoria para PCD");
        // O estouro de int que o parser antigo gravava como -694967296.
        inserirCliente("Joao Estouro", "29537941070", "300000000 anos", "Aposentadoria rural");
    }

    private void inserirCliente(String nome, String cpf, String tempo, String beneficio)
            throws Exception {
        executar(
                String.format(
                        "INSERT INTO %s.clients (full_name, birth_date, cpf, mother_name,"
                                + " mobile_phone, inss_password, gender, situation, benefit,"
                                + " contribution_time) VALUES ('%s','1960-01-01','%s','Mae','(31)9','x',"
                                + "'Feminino','Análise documental','%s','%s')",
                        SCHEMA, nome, cpf, beneficio, tempo));
    }

    private void conferirV15() throws Exception {
        // Um CPF, um cadastro ativo. O mais novo foi excluído logicamente, não apagado.
        assertEquals(
                1,
                contar(
                        "SELECT count(*) FROM "
                                + SCHEMA
                                + " .clients WHERE cpf = '39053344705'".replace(" .", ".")
                                + " AND deleted_at IS NULL"),
                "a V15 deveria ter deixado só o cadastro mais antigo ativo");
        assertEquals(
                2,
                contar("SELECT count(*) FROM " + SCHEMA + ".clients WHERE cpf = '39053344705'"),
                "o duplicado tem de continuar no banco, excluído logicamente - nada é apagado");
        assertTrue(
                contar("SELECT count(*) FROM " + SCHEMA + ".audit_log WHERE detail LIKE 'V15:%'")
                        > 0,
                "a V15 mexeu em dado e não deixou rastro em audit_log");
    }

    private void conferirV16() throws Exception {
        assertEquals(
                3,
                contar(
                        "SELECT contribution_years FROM "
                                + SCHEMA
                                + ".clients WHERE full_name = 'Maria Antiga'"),
                "'3 anos, 10 meses, 22 dias' deveria ter virado 3/10/22");
        assertEquals(
                47,
                contar(
                        "SELECT contribution_in_months FROM "
                                + SCHEMA
                                + ".clients WHERE full_name = 'Maria Antiga'"),
                "o total em meses mudou de valor para quem já estava cadastrado");

        // O que não dá para entender vira NULL - "não informado" é diferente de zero - e é
        // registrado com o texto original, para alguém reconferir com o cliente.
        assertNull(
                valor(
                        "SELECT contribution_years FROM "
                                + SCHEMA
                                + ".clients WHERE full_name = 'Joao Estouro'"),
                "'300000000 anos' não pode virar número nenhum");
        assertTrue(
                contar("SELECT count(*) FROM " + SCHEMA + ".audit_log WHERE detail LIKE 'V16:%'")
                        >= 1,
                "texto não reconhecido tem de ficar registrado, não sumir em silêncio");
    }

    private void conferirV17() throws Exception {
        assertEquals(
                0,
                contar(
                        "SELECT count(*) FROM "
                                + SCHEMA
                                + ".clients WHERE benefit IN ('Aposentadoria por invalidez',"
                                + " 'Aposentadoria para PCD')"),
                "nome aposentado de benefício continuou gravado");
        assertEquals(
                1,
                contar(
                        "SELECT count(*) FROM "
                                + SCHEMA
                                + ".clients WHERE benefit = 'Aposentadoria por incapacidade"
                                + " permanente'"),
                "invalidez deveria ter virado incapacidade permanente");
    }

    // ── Infraestrutura do teste ──

    /** {@code null} = até a última. Roda as migrations de tenant no schema de teste. */
    private void migrarAte(String versao) {
        var config =
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(SCHEMA)
                        .locations("classpath:db/migration/tenant");
        if (versao != null) {
            config = config.target(MigrationVersion.fromVersion(versao));
        }
        config.load().migrate();
    }

    private void executar(String sql) throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private Integer valor(String sql) throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            int v = rs.getInt(1);
            return rs.wasNull() ? null : v;
        }
    }

    private int contar(String sql) throws Exception {
        Integer v = valor(sql);
        return v == null ? 0 : v;
    }
}
