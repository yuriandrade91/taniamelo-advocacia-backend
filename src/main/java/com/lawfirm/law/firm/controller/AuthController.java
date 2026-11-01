package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.LoginRequestDTO;
import com.lawfirm.law.firm.dto.LoginResponseDTO;
import com.lawfirm.law.firm.security.RefreshTokenCookie;
import com.lawfirm.law.firm.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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

    private final AuthService authService;
    private final RefreshTokenCookie refreshCookie;

    public AuthController(AuthService authService, RefreshTokenCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
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
        return withRefreshCookie(
                authService.login(
                        request.getLogin(),
                        request.getPassword(),
                        clientIp(httpRequest),
                        httpRequest.getHeader(HttpHeaders.USER_AGENT)));
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
        return withRefreshCookie(
                authService.refresh(
                        refreshCookie.read(httpRequest),
                        httpRequest.getHeader(HttpHeaders.USER_AGENT)));
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
        authService.logout(refreshCookie.read(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.cleared().toString())
                .body(ApiResponse.successObject("Logout efetuado."));
    }

    // ── Helpers ──

    private ResponseEntity<ApiResponse<LoginResponseDTO>> withRefreshCookie(
            AuthService.Session session) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie
                                .issue(session.refreshToken(), session.refreshTtlSeconds())
                                .toString())
                .body(ApiResponse.successObject(session.body()));
    }

    /**
     * IP de quem chamou.
     *
     * <p>Usa {@code X-Forwarded-For} quando presente porque a instância pode ficar atrás de proxy;
     * sem isso, todo mundo apareceria com o IP do proxy e o limite por IP viraria um limite global,
     * derrubando o escritório inteiro no primeiro ataque.
     *
     * <p>O cabeçalho é forjável por quem chama direto na instância - por isso ele é uma das duas
     * chaves, nunca a única: o limite por login não depende dele.
     */
    private static String clientIp(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
