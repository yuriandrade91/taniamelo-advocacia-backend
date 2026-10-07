package com.lawfirm.law.firm.config;

import com.lawfirm.law.firm.model.SupportUser;
import com.lawfirm.law.firm.repository.SupportUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante um agente de SUPORTE inicial em {@code public.support_users}, para que a equipe da
 * plataforma consiga entrar sem depender de nenhum tenant. Credenciais vêm de APP_SUPPORT_EMAIL /
 * APP_SUPPORT_PASSWORD; em dev caem num default óbvio que deve ser trocado.
 *
 * <p>Só cria quando a tabela está vazia (control-plane, não por-tenant). Não precisa de {@code
 * TenantContext}: a entidade é qualificada com o schema {@code public}.
 */
@Component
public class SupportUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SupportUserSeeder.class);

    private final SupportUserRepository supportUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public SupportUserSeeder(
            SupportUserRepository supportUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.support.email:suporte@plataforma.adv.br}") String email,
            @Value("${app.support.password:changeme123}") String password) {
        this.supportUserRepository = supportUserRepository;
        this.passwordEncoder = passwordEncoder;
        // Um env var presente porém VAZIO (ex.: compose passando ${APP_SUPPORT_EMAIL:-}) não aciona
        // o default do @Value - o Spring usa a string vazia. Tratamos em branco como ausente.
        this.email = (email == null || email.isBlank()) ? "suporte@plataforma.adv.br" : email;
        this.password = (password == null || password.isBlank()) ? "changeme123" : password;
    }

    @Override
    public void run(String... args) {
        if (supportUserRepository.count() > 0) {
            return;
        }
        SupportUser agent = new SupportUser();
        agent.setFullName("Suporte da Plataforma");
        agent.setEmail(email);
        agent.setPasswordHash(passwordEncoder.encode(password));
        agent.setActive(true);
        supportUserRepository.save(agent);

        log.warn(
                "public.support_users vazio - agente de suporte inicial criado ({}). Troque a senha "
                        + "e defina APP_SUPPORT_EMAIL/APP_SUPPORT_PASSWORD em produção.",
                email);
    }
}
