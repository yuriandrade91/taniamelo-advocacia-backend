package com.lawfirm.law.firm.security;

import com.lawfirm.law.firm.tenant.TenantContext;
import com.lawfirm.law.firm.tenant.TenantRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
                        && tokenPerteceAoEscritorioDaRequisicao(token)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String email = jwtService.extractEmail(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception ex) {
                // Invalid/expired token: leave context unauthenticated, let Spring Security respond
                // 401/403.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
