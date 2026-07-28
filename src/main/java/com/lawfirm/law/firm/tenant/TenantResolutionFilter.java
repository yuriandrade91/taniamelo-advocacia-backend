package com.lawfirm.law.firm.tenant;

import com.lawfirm.law.firm.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolve o tenant (schema) da requisição e o coloca no {@link TenantContext} antes de qualquer
 * acesso ao banco. Ordem de resolução:
 *
 * <ol>
 *   <li>Cabeçalho {@code X-Tenant-Id} (o frontend envia sempre, inclusive no login — quando ainda
 *       não há JWT para descobrir o tenant).
 *   <li>Claim {@code tenant} do JWT (requisições já autenticadas que não mandem o header).
 *   <li>Nada → o resolver cai no schema default.
 * </ol>
 *
 * Só aceita schemas configurados em {@link TenancyProperties}. Limpa o contexto no fim, sempre.
 * Registrado cedo na cadeia (antes do filtro de autenticação) em {@code SecurityConfig}.
 */
@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final TenancyProperties properties;
    private final JwtService jwtService;
    private final TenantRegistry tenantRegistry;

    public TenantResolutionFilter(
            TenancyProperties properties, JwtService jwtService, TenantRegistry tenantRegistry) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.tenantRegistry = tenantRegistry;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenant = resolve(request);
            if (tenant != null) {
                TenantContext.set(tenant);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * O valor do header/claim é o identificador PÚBLICO (UUID ou slug); o registry o traduz para o
     * nome do schema, que é o que o {@link TenantContext} guarda. O schema físico nunca trafega.
     */
    private String resolve(HttpServletRequest request) {
        String fromHeader = request.getHeader(properties.getHeaderName());
        String schema = tenantRegistry.schemaFor(fromHeader).orElse(null);
        if (schema != null) {
            return schema;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER)) {
            try {
                String publicId = jwtService.extractTenant(auth.substring(BEARER.length()));
                return tenantRegistry.schemaFor(publicId).orElse(null);
            } catch (RuntimeException ignored) {
                // token inválido/sem claim: cai no default
            }
        }
        return null;
    }
}
