package com.lawfirm.law.firm.config;

import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import com.lawfirm.law.firm.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante que sempre exista pelo menos um usuário ADMIN para acessar a API. Credenciais vêm de
 * variáveis de ambiente (APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD); em dev, caem em um default óbvio
 * que deve ser trocado no primeiro login.
 */
@Component
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenancyProperties tenancyProperties;
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TenancyProperties tenancyProperties,
            @Value("${app.admin.email:admin@taniamelo.adv.br}") String adminEmail,
            @Value("${app.admin.password:changeme123}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenancyProperties = tenancyProperties;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /**
     * Garante um ADMIN inicial em CADA schema de tenant (cada schema tem sua própria tabela de
     * usuários). Só cria onde a tabela estiver vazia - schemas já populados (ex.: pelas massas de
     * mock) são deixados como estão.
     */
    @Override
    public void run(String... args) {
        for (String schema : tenancyProperties.getSchemas()) {
            try {
                TenantContext.set(schema);
                seedFor(schema);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void seedFor(String schema) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setFullName("Administrador");
        admin.setEmail(adminEmail);
        admin.setUsername(usernameFromEmail(adminEmail));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        log.warn(
                "Schema '{}' sem usuários - ADMIN inicial criado ({}). Troque a senha e defina "
                        + "APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD em produção.",
                schema,
                adminEmail);
    }

    private static String usernameFromEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
