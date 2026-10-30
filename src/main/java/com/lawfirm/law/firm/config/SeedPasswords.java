package com.lawfirm.law.firm.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;

/**
 * Senha dos usuários criados no boot ({@link AdminUserSeeder}, {@link SupportUserSeeder}).
 *
 * <p>Em dev o default {@code changeme123} é conveniência. Fora de dev/test, criar um usuário com
 * ele é publicar a senha: ela está no código-fonte. E o agente de suporte entra em TODOS os
 * escritórios. O deploy já recusa {@code APP_ADMIN_PASSWORD=changeme123} escrito no .env, mas não
 * pega a variável ausente ou vazia - que cai no mesmo default dentro do seeder. Por isso a checagem
 * mora aqui, no único ponto por onde a senha vira usuário.
 *
 * <p>Só vale no momento de CRIAR: um usuário que já existe não é tocado pelos seeders, então uma
 * instância já semeada continua subindo mesmo sem a variável.
 */
final class SeedPasswords {

    static final String DEV_DEFAULT = "changeme123";

    // Mesmos perfis que CorsConfig deixa usar "*": a máquina do desenvolvedor e o contexto de
    // teste.
    private static final List<String> PERFIS_LOCAIS = List.of("dev", "test");

    private SeedPasswords() {}

    static void requireRealPasswordOutsideDev(
            String password, String envVar, String quem, Environment env) {
        boolean local = Arrays.stream(env.getActiveProfiles()).anyMatch(PERFIS_LOCAIS::contains);
        if (local) {
            return;
        }
        if (password == null || password.isBlank() || DEV_DEFAULT.equals(password)) {
            throw new IllegalStateException(
                    "Recusando criar "
                            + quem
                            + " com senha vazia ou a de exemplo fora dos perfis dev/test. Defina "
                            + envVar
                            + " com uma senha real.");
        }
    }
}
