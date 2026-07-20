package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.LoginRequestDTO;
import com.lawfirm.law.firm.dto.LoginResponseDTO;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Login e emissão de token JWT")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Operation(
            summary = "Login",
            description =
                    "Autentica com e-mail/senha e devolve um token JWT (Bearer) "
                            + "a ser usado no header Authorization dos demais endpoints. Único endpoint que não exige token.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request) {
        // Throws BadCredentialsException / DisabledException on failure, handled by
        // GlobalExceptionHandler.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user =
                userRepository
                        .findByEmailIgnoreCase(request.getEmail())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Usuário autenticado não encontrado"));

        String token =
                jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        LoginResponseDTO body =
                new LoginResponseDTO(
                        token,
                        jwtService.getExpirationMillis() / 1000,
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole().name());

        return ResponseEntity.ok(ApiResponse.successObject(body));
    }
}
