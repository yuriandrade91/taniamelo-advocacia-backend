package com.lawfirm.law.firm.security;

import com.lawfirm.law.firm.tenant.TenantContext;
import com.lawfirm.law.firm.tenant.TenantRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TenantRegistry tenantRegistry;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            TenantRegistry tenantRegistry) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tenantRegistry = tenantRegistry;
    }

    /**
     * O token vale para o escritório que o emitiu - e para nenhum outro.
     *
     * <p>Sem esta checagem, o {@code X-Tenant-Id} do cabeçalho decidia sozinho em qual schema
     * procurar o usuário ({@link com.lawfirm.law.firm.tenant.TenantResolutionFilter} resolve pelo
     * header primeiro), e o usuário era carregado pelo e-mail LÁ. Como o {@link
     * com.lawfirm.law.firm.config.AdminUserSeeder} semeia o mesmo e-mail e a mesma senha em todos
     * os schemas, existia por construção uma conta presente em todos os escritórios: com o token de
     * um e o cabeçalho de outro, dava para ler a carteira de clientes alheia, criar compromisso
     * nela e ler a senha do INSS dos clientes dela.
     *
     * <p>Comparamos schema resolvido contra schema do claim, e não o texto do claim contra o texto
     * do header, porque o identificador público aceita slug OU UUID: {@code demo} e o UUID do mesmo
     * escritório são o mesmo lugar, e comparar strings recusaria isso.
     */
    private boolean tokenPerteceAoEscritorioDaRequisicao(String token) {
        String schemaDaRequisicao = TenantContext.get();
        if (schemaDaRequisicao == null) {
            return false;
        }
        String schemaDoToken =
                tenantRegistry.schemaFor(jwtService.extractTenant(token)).orElse(null);
        return schemaDaRequisicao.equals(schemaDoToken);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                if (jwtService.isValid(token)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    autenticar(token, request);
                }
            } catch (Exception ex) {
                // Invalid/expired token: leave context unauthenticated, let Spring Security respond
                // 401/403.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Despacha pelo tipo de token: plataforma (suporte), sessão de suporte, ou usuário do tenant. */
    private void autenticar(String token, HttpServletRequest request) {
        String scope = jwtService.extractScope(token);
        if ("platform".equals(scope)) {
            autenticarPlataforma(token, request);
        } else if (jwtService.extractActingSupportId(token) != null) {
            autenticarSessaoDeSuporte(token, request);
        } else {
            autenticarUsuarioDoTenant(token, request);
        }
    }

    /**
     * Token de plataforma do suporte: só vale nos endpoints /api/v1/support/** (abrir sessão). Não
     * carrega tenant e não pode agir sobre dado de escritório - por isso é recusado em qualquer
     * outra rota.
     */
    private void autenticarPlataforma(String token, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/v1/support")) {
            return;
        }
        UUID supportId = jwtService.extractUid(token);
        if (supportId == null) {
            return;
        }
        var authToken =
                new UsernamePasswordAuthenticationToken(
                        supportId, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM")));
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    /**
     * Sessão de suporte (impersonation): o token traz o tenant alvo e role ADMIN. Vale a mesma
     * regra de isolamento (o tenant do token tem de bater com o da requisição), mas o principal é
     * SINTÉTICO - o agente de suporte não existe na tabela users do escritório, então nada é
     * carregado do banco. O id do ator viaja no principal para a auditoria.
     */
    private void autenticarSessaoDeSuporte(String token, HttpServletRequest request) {
        if (!tokenPerteceAoEscritorioDaRequisicao(token)) {
            return;
        }
        UUID actingSupportId = jwtService.extractActingSupportId(token);
        String role = jwtService.extractRole(token);
        String email = jwtService.extractEmail(token);
        if (actingSupportId == null || role == null) {
            return;
        }
        UserPrincipal principal = UserPrincipal.impersonation(actingSupportId, email, role);
        var authToken =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    /** Fluxo normal: usuário real do schema do tenant, carregado por e-mail. */
    private void autenticarUsuarioDoTenant(String token, HttpServletRequest request) {
        if (!tokenPerteceAoEscritorioDaRequisicao(token)) {
            return;
        }
        String email = jwtService.extractEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (userDetails.isEnabled()) {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }
}
