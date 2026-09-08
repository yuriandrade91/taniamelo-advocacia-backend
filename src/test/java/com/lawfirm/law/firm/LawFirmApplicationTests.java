package com.lawfirm.law.firm;

import com.lawfirm.law.firm.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sobe o contexto inteiro contra um Postgres real. É o teste mais barato que pega bean quebrado,
 * migration inválida e mapeamento de entidade fora de sincronia com o schema - todos erros que só
 * apareceriam no deploy.
 */
@DisplayName("LawFirmApplication: ponto de entrada da aplicação")
class LawFirmApplicationTests extends PostgresIntegrationTest {

    @Test
    @DisplayName("o contexto sobe e as migrations são aplicadas em cada schema de tenant")
    void contextLoads() {}
}
