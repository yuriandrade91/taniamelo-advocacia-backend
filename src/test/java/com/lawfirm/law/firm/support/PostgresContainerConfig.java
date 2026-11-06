package com.lawfirm.law.firm.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres real em container para os testes que sobem o contexto do Spring.
 *
 * <p><b>Por que não um banco em memória.</b> A aplicação é multi-tenant por schema: o {@link
 * com.lawfirm.law.firm.tenant.MultiTenantFlywayMigrator} cria um schema por escritório e aplica
 * {@code db/migration/tenant} em cada um, o {@code search_path} troca de schema por requisição e as
 * migrations compartilhadas instalam a extensão {@code unaccent}. Nada disso existe em H2 - um
 * teste que passasse ali não diria nada sobre o que roda em produção.
 *
 * <p><b>Um container para toda a suíte.</b> A anotação {@code @ServiceConnection} aponta o
 * datasource para o container sem {@code @DynamicPropertySource}. Como todas as classes de teste de
 * contexto herdam de {@link PostgresIntegrationTest}, elas compartilham a mesma configuração e
 * portanto o mesmo contexto em cache - o container sobe uma vez por execução, não uma vez por
 * classe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    /**
     * Versão fixada de propósito: a imagem é parte do que o teste afirma. Acompanha a versão de
     * produção, hoje {@code postgres:17.5} no docker-compose.yml - a mesma tag, não a variante
     * alpine, porque alpine usa outra libc e portanto outro comportamento de collation e de {@code
     * unaccent}, que é justamente o que a busca de clientes depende.
     *
     * <p>Esta constante e a imagem do compose têm de mudar juntas. Divergir faz a suíte afirmar
     * sobre um Postgres que não é o que atende o cliente.
     */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:17.5");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }
}
