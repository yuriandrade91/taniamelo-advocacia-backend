package com.lawfirm.law.firm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

/**
 * Guarda o invariante do multi-tenancy por schema: quem aplica migration aqui é o {@code
 * MultiTenantFlywayMigrator}, nunca a auto-config do Boot.
 *
 * <p>A auto-config varre {@code classpath:db/migration} inteiro e acha duas migrations de versão 1
 * ({@code shared/V1__extensions.sql} e {@code tenant/V1__users.sql}) — a aplicação não sobe. Deixar
 * isso só em {@code spring.flyway.enabled: false} não basta: propriedade se sobrescreve de fora
 * (env var, {@code -D} da IDE, compose) e o erro volta na máquina de alguém, não no CI.
 */
@DisplayName("Flyway: a auto-configuração do Boot fica excluída no código")
class FlywayAutoConfigDesligadaTest {

    @Test
    @DisplayName("LawFirmApplication exclui a FlywayAutoConfiguration")
    void excluiFlywayAutoConfiguration() {
        SpringBootApplication anotacao =
                LawFirmApplication.class.getAnnotation(SpringBootApplication.class);

        assertTrue(
                Arrays.asList(anotacao.exclude()).contains(FlywayAutoConfiguration.class),
                "A auto-config do Flyway precisa continuar excluída: sem isso, uma variável de"
                        + " ambiente reativa a varredura achatada de db/migration e a aplicação"
                        + " para de subir (\"Found more than one migration with version 1\").");
    }
}
