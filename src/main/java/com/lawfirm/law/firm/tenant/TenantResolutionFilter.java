package com.lawfirm.law.firm.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.law.firm.dto.ApiError;
import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * <p><b>Header presente e desconhecido é recusado com 400</b>, não ignorado. Ignorá-lo fazia a
 * requisição cair no schema default: um erro de digitação no {@code X-Tenant-Id} levava a sessão
 * para o escritório errado — e como o default é o escritório real, o modo de falha era o pior
 * possível, silencioso e na base que mais importa. Ausência do header continua válida (o claim do
 * JWT resolve, e o boot/os jobs precisam do default); o que não pode é um valor errado passar como
 * se fosse certo.
 *
 * <p>Só aceita tenants ativos no catálogo {@code public.tenants}. Limpa o contexto no fim, sempre.
 * Registrado cedo na cadeia (antes do filtro de autenticação) em {@code SecurityConfig}.
 */
@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final TenancyProperties properties;
    private final JwtService jwtService;
    private final TenantRegistry tenantRegistry;

    /**
     * ObjectMapper próprio, pelo mesmo motivo de {@code RestAuthenticationEntryPoint}: aqui só se
     * serializa um envelope de erro minúsculo, e o filtro roda antes do MVC — não há
     * {@code @ControllerAdvice} para formatar a resposta neste ponto da cadeia.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            String informado = request.getHeader(properties.getHeaderName());
            if (informado != null
                    && !informado.isBlank()
                    && tenantRegistry.schemaFor(informado).isEmpty()) {
                recusar(response);
                return;
            }

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
     * 400 no envelope padrão, sem dizer quais escritórios existem: a mensagem confirmaria ou
     * negaria a existência de um escritório para quem está apenas tentando adivinhar.
     */
    private void recusar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> corpo =
                ApiResponse.error(
                        List.of(
                                new ApiError(
                                        properties.getHeaderName(),
                                        "Escritório não encontrado.",
                                        "TENANT_NOT_FOUND")));
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
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
