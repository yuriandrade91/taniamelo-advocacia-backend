package com.lawfirm.law.firm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

/**
 * A auto-configuração do Flyway fica <b>excluída no código</b>, não só desligada por propriedade.
 *
 * <p>Com multi-tenancy por schema as migrations moram em duas pastas — {@code db/migration/shared}
 * (aplicada no {@code public}) e {@code db/migration/tenant} (aplicada em cada schema) — e quem as
 * aplica é o {@code MultiTenantFlywayMigrator}. A auto-config do Boot varre a pasta raiz {@code
 * classpath:db/migration} de uma vez só: ela enxerga {@code shared/V1__extensions.sql} e {@code
 * tenant/V1__users.sql} como duas migrations de versão 1 e a aplicação nem sobe ("Found more than
 * one migration with version 1"). Se subisse, seria pior: rodaria tudo achatado no schema errado.
 *
 * <p>{@code spring.flyway.enabled: false} continua nos YAMLs como documentação, mas propriedade
 * qualquer um sobrescreve de fora — {@code SPRING_FLYWAY_ENABLED=true} no shell, um {@code -D} na
 * configuração de execução da IDE, um env var no compose. O {@code exclude} aqui é estrutural: vale
 * em dev, em teste, no container e no CI, sem depender de ninguém lembrar.
 */
@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
public class LawFirmApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawFirmApplication.class, args);
    }
}
