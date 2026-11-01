package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.LoginResponseDTO;
import com.lawfirm.law.firm.model.Tenant;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.JwtService;
import com.lawfirm.law.firm.security.LoginGuard;
import com.lawfirm.law.firm.tenant.CurrentTenant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Login, renovação e logout. O que é HTTP (cookie, IP, cabeçalhos) fica no {@code AuthController};
 * aqui o resultado é uma {@link Session} que ele só precisa entregar.
 *
 * <p>Sem {@code @Transactional} nos métodos: {@link RefreshTokenService#rotate} precisa confirmar a
 * revogação mesmo quando recusa o token, e uma transação em volta dela desfaria isso ao ver a
 * exceção passar.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final CurrentTenant currentTenant;
    private final LoginGuard loginGuard;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            CurrentTenant currentTenant,
            LoginGuard loginGuard) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.currentTenant = currentTenant;
        this.loginGuard = loginGuard;
    }

    /** O corpo da resposta e o refresh token em claro, que só existe aqui e no cookie. */
    public record Session(LoginResponseDTO body, String refreshToken, long refreshTtlSeconds) {}

    public Session login(String login, String password, String ip, String userAgent) {
        // As duas checagens antes de verificar a senha: quem já estourou o limite não deve
        // gastar CPU de hashing, que é exatamente o que um ataque quer forçar.
        loginGuard.ensureAllowed(ip, login);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login, password));
        } catch (AuthenticationException ex) {
            loginGuard.registerFailure(ip, login);
            throw ex;
        }
        loginGuard.registerSuccess(login);

        User user =
                userRepository
                        .findByEmailIgnoreCaseOrUsernameIgnoreCase(login, login)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Usuário autenticado não encontrado"));
        return session(user, refreshTokenService.issue(user.getId(), userAgent).rawToken());
    }

    public Session refresh(String refreshToken, String userAgent) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken, userAgent);

        // Desativar alguém tem de encerrar o acesso: sem o filtro, a renovação responderia 200 com
        // um token que o filtro de autenticação recusa na requisição seguinte.
        User user =
                userRepository
                        .findById(rotation.userId())
                        .filter(found -> Boolean.TRUE.equals(found.getActive()))
                        .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado"));
        return session(user, rotation.rawToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private Session session(User user, String refreshToken) {
        Tenant tenant = currentTenant.get();
        String access =
                jwtService.generateToken(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name(),
                        tenant.getId().toString());

        LoginResponseDTO body =
                new LoginResponseDTO(
                        access,
                        jwtService.getExpirationMillis() / 1000,
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole().name());
        body.setTenantId(tenant.getId().toString());
        body.setTenantSlug(tenant.getSlug());
        return new Session(body, refreshToken, refreshTokenService.getTtlSeconds());
    }
}
