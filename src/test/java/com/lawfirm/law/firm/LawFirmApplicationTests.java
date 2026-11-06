package com.lawfirm.law.firm;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Sobe o contexto inteiro contra um Postgres real. É o teste mais barato que pega bean quebrado,
 * migration inválida e mapeamento de entidade fora de sincronia com o schema - todos erros que só
 * apareceriam no deploy.
 */
@DisplayName("LawFirmApplication: ponto de entrada da aplicação")
class LawFirmApplicationTests extends PostgresIntegrationTest {

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("o contexto sobe e as migrations são aplicadas em cada schema de tenant")
    void contextLoads() {
        assertNotNull(context);
    }
}
