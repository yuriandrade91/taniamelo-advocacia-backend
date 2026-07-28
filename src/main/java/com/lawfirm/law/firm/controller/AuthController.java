package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.LoginRequestDTO;
import com.lawfirm.law.firm.dto.LoginResponseDTO;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.TenantRepository;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.JwtService;
import com.lawfirm.law.firm.service.RefreshTokenService;
import com.lawfirm.law.firm.tenant.TenancyProperties;
import com.lawfirm.law.firm.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Autenticação",
        description = "Login (email ou username), renovação (refresh em cookie httpOnly) e logout")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenService refreshTokenService;
    private final TenancyProperties tenancyProperties;

    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            RefreshTokenService refreshTokenService,
            TenancyProperties tenancyProperties,
            @Value("${app.auth.refresh-cookie.name:refreshToken}") String cookieName,
            @Value("${app.auth.refresh-cookie.path:/api/v1/auth}") String cookiePath,
            @Value("${app.auth.refresh-cookie.secure:false}") boolean cookieSecure,
            @Value("${app.auth.refresh-cookie.same-site:Lax}") String cookieSameSite) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenService = refreshTokenService;
        this.tenancyProperties = tenancyProperties;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @Operation(
            summary = "Login",
            description =
                    "Autentica com login (e-mail OU username) + senha e devolve um access token (JWT)"
                            + " no corpo e um refresh token em cookie httpOnly. Informe o tenant no"
                            + " cabeçalho X-Tenant-Id usando o SLUG ou o UUID do"
                            + " escritório — nunca o nome do schema. Escolha um dos exemplos abaixo"
                            + " (usuários reais, ADMIN e STAFF de cada tenant); o X-Tenant-Id indicado"
                            + " no nome do exemplo é o que deve ir no cabeçalho da requisição.")
    @Parameter(
            name = "X-Tenant-Id",
            in = ParameterIn.HEADER,
            required = true,
            example = "tania",
            description =
                    "Slug (`tania`/`demo`) ou UUID do escritório - troque para bater com o exemplo"
                            + " do corpo escolhido acima. Preencher aqui NÃO substitui o campo"
                            + " tenantHeader em Authorize; mantenha os dois com o mesmo valor.")
    @SecurityRequirement(name = "tenantHeader")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(implementation = LoginRequestDTO.class),
                                            examples = {
                                                @ExampleObject(
                                                        name =
                                                                "Tania ADMIN - por e-mail (X-Tenant-Id: tania)",
                                                        value =
                                                                "{\"login\":\"dra.tania@taniamelo.adv.br\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Tania ADMIN - por username (X-Tenant-Id: tania)",
                                                        value =
                                                                "{\"login\":\"dra.tania\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Tania STAFF - por e-mail (X-Tenant-Id: tania)",
                                                        value =
                                                                "{\"login\":\"ana.souza@taniamelo.adv.br\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Tania STAFF - por username (X-Tenant-Id: tania)",
                                                        value =
                                                                "{\"login\":\"ana.souza\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Demo ADMIN - por e-mail (X-Tenant-Id: demo)",
                                                        value =
                                                                "{\"login\":\"dr.almeida@escritoriodemo.adv.br\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Demo ADMIN - por username (X-Tenant-Id: demo)",
                                                        value =
                                                                "{\"login\":\"dr.almeida\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Demo STAFF - por e-mail (X-Tenant-Id: demo)",
                                                        value =
                                                                "{\"login\":\"bruna.lima@escritoriodemo.adv.br\",\"password\":\"password\"}"),
                                                @ExampleObject(
                                                        name =
                                                                "Demo STAFF - por username (X-Tenant-Id: demo)",
                                                        value =
                                                                "{\"login\":\"bruna.lima\",\"password\":\"password\"}")
                                            }))
                    @Valid
                    @RequestBody
                    LoginRequestDTO request,
            HttpServletRequest httpRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLogin(), request.getPassword()));

        User user =
                userRepository
                        .findByEmailIgnoreCaseOrUsernameIgnoreCase(
                                request.getLogin(), request.getLogin())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Usuário autenticado não encontrado"));

        Tenant tenant = currentTenant();
        String access =
                jwtService.generateToken(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name(),
                        tenant.getId().toString());
        RefreshTokenService.Issued refresh =
                refreshTokenService.issue(user.getId(), httpRequest.getHeader("User-Agent"));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refresh.rawToken()).toString())
                .body(ApiResponse.successObject(buildBody(access, user, tenant)));
    }

    @Operation(
            summary = "Renovar sessão",
            description =
                    "Lê o refresh token do cookie httpOnly, rotaciona-o (o anterior é invalidado) e"
                            + " devolve um novo access token. Envie o cabeçalho X-Tenant-Id do mesmo"
                            + " tenant.")
    @Parameter(
            name = "X-Tenant-Id",
            in = ParameterIn.HEADER,
            required = true,
            example = "tania",
            description = "Slug (`tania`/`demo`) ou UUID do escritório - o mesmo da sessão.")
    @SecurityRequirement(name = "tenantHeader")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> refresh(HttpServletRequest httpRequest) {
        RefreshTokenService.Rotation rotation =
                refreshTokenService.rotate(
                        readCookie(httpRequest), httpRequest.getHeader("User-Agent"));

        User user =
                userRepository
                        .findById(rotation.userId())
                        .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado"));

        Tenant tenant = currentTenant();
        String access =
                jwtService.generateToken(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name(),
                        tenant.getId().toString());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotation.rawToken()).toString())
                .body(ApiResponse.successObject(buildBody(access, user, tenant)));
    }

    @Operation(
            summary = "Logout",
            description = "Revoga o refresh token corrente e limpa o cookie.")
    @Parameter(
            name = "X-Tenant-Id",
            in = ParameterIn.HEADER,
            required = true,
            example = "tania",
            description = "Slug (`tania`/`demo`) ou UUID do escritório - o mesmo da sessão.")
    @SecurityRequirement(name = "tenantHeader")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest httpRequest) {
        refreshTokenService.revoke(readCookie(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .body(ApiResponse.successObject("Logout efetuado."));
    }

    // ── Helpers ──

    private LoginResponseDTO buildBody(String access, User user, Tenant tenant) {
        LoginResponseDTO body =
                new LoginResponseDTO(
                        access,
                        jwtService.getExpirationMillis() / 1000,
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole().name());
        body.setTenantId(tenant.getId().toString());
        body.setTenantSlug(tenant.getSlug());
        return body;
    }

    /**
     * Registro do tenant corrente (resolvido pelo TenantResolutionFilter), com id/slug públicos.
     */
    private Tenant currentTenant() {
        String current = TenantContext.get();
        final String schema = current != null ? current : tenancyProperties.getDefaultSchema();
        return tenantRepository
                .findBySchemaName(schema)
                .orElseThrow(() -> new IllegalStateException("Tenant não encontrado: " + schema));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(refreshTokenService.getTtlSeconds())
                .build();
    }

    private ResponseCookie clearedCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(0)
                .build();
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
