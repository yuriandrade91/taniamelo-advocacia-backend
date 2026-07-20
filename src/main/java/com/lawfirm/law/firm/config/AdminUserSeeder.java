package com.lawfirm.law.firm.config;

import com.lawfirm.law.firm.model.Role;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
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
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:admin@taniamelo.adv.br}") String adminEmail,
            @Value("${app.admin.password:changeme123}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setFullName("Administrador");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        log.warn(
                "Nenhum usuário encontrado - usuário ADMIN inicial criado ({}). "
                        + "Troque a senha assim que possível e defina APP_ADMIN_EMAIL/APP_ADMIN_PASSWORD em produção.",
                adminEmail);
    }
}
