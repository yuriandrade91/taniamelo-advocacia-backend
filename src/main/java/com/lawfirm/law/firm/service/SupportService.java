package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.SupportSessionResponseDTO;
import com.lawfirm.law.firm.dto.SupportTokenResponseDTO;
import com.lawfirm.law.firm.exception.BusinessErrorCode;
import com.lawfirm.law.firm.exception.BusinessException;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.model.SupportUser;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.repository.SupportUserRepository;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.security.JwtService;
import com.lawfirm.law.firm.security.LoginThrottleService;
import com.lawfirm.law.firm.tenant.TenantRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Acesso da equipe de suporte da plataforma aos tenants, em dois passos: (1) login no control-plane
 * ({@code public.support_users}) devolve um token de plataforma; (2) com ele, abre-se uma sessão de
 * suporte num tenant específico, que devolve um token curto de impersonation com acesso total. Toda
 * ação feita com esse token é atribuída ao agente na auditoria ({@code acting_support_user_id}).
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportUserRepository supportUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantRegistry tenantRegistry;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottleService loginThrottle;

    public SupportService(
            SupportUserRepository supportUserRepository,
            TenantRepository tenantRepository,
            TenantRegistry tenantRegistry,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginThrottleService loginThrottle) {
        this.supportUserRepository = supportUserRepository;
        this.tenantRepository = tenantRepository;
        this.tenantRegistry = tenantRegistry;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginThrottle = loginThrottle;
    }

    /** Autentica o agente no control-plane e devolve o token de plataforma. */
    public SupportTokenResponseDTO login(String email, String rawPassword, String ip) {
        loginThrottle.ensureAllowed(ip, email);

        SupportUser agent = supportUserRepository.findByEmailIgnoreCase(email).orElse(null);
        // Mesma resposta para "não existe" e "senha errada": não revela quem é agente de suporte.
        if (agent == null
                || !Boolean.TRUE.equals(agent.getActive())
                || !passwordEncoder.matches(rawPassword, agent.getPasswordHash())) {
            loginThrottle.registerFailure(ip, email);
            throw new BadCredentialsException("Credenciais inválidas");
        }
        loginThrottle.registerSuccess(email);

        String token = jwtService.generatePlatformToken(agent.getId(), agent.getEmail());
        return new SupportTokenResponseDTO(
                token,
                "Bearer",
                jwtService.getSupportPlatformMillis() / 1000,
                agent.getFullName(),
                agent.getEmail());
    }

    /** Abre uma sessão de suporte (impersonation) num tenant ativo e devolve o token curto. */
    public SupportSessionResponseDTO openSession(UUID supportUserId, String tenantIdentifier) {
        SupportUser agent =
                supportUserRepository
                        .findById(supportUserId)
                        .filter(u -> Boolean.TRUE.equals(u.getActive()))
                        .orElseThrow(() -> new BadCredentialsException("Sessão de suporte inválida"));

        String schema =
                tenantRegistry
                        .schemaFor(tenantIdentifier)
                        .orElseThrow(() -> NotFoundException.of("Tenant", tenantIdentifier));
        Tenant tenant =
                tenantRepository
                        .findBySchemaName(schema)
                        .orElseThrow(() -> NotFoundException.of("Tenant", tenantIdentifier));
        if (!"ativo".equalsIgnoreCase(tenant.getStatus())) {
            throw new BusinessException(
                    BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Escritório inativo - sessão de suporte recusada.");
        }

        String token =
                jwtService.generateSupportSessionToken(
                        agent.getId(), agent.getEmail(), tenant.getId().toString());

        // Registro imediato da abertura da sessão (além do rastro por ação no audit_log).
        log.info(
                "Sessão de suporte aberta: agente={} ({}) tenant={} schema={}",
                agent.getId(),
                agent.getEmail(),
                tenant.getId(),
                schema);

        return new SupportSessionResponseDTO(
                token,
                "Bearer",
                jwtService.getSupportSessionMillis() / 1000,
                tenant.getId().toString(),
                tenant.getSlug(),
                tenant.getRazaoSocial());
    }
}
