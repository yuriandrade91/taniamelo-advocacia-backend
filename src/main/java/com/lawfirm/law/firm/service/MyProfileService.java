package com.lawfirm.law.firm.service;

import com.lawfirm.law.firm.dto.ChangePasswordRequestDTO;
import com.lawfirm.law.firm.dto.MyProfileDTO;
import com.lawfirm.law.firm.dto.MyProfileUpdateRequestDTO;
import com.lawfirm.law.firm.exception.NotFoundException;
import com.lawfirm.law.firm.exception.ValidationErrorCode;
import com.lawfirm.law.firm.exception.ValidationException;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os próprios dados de quem está autenticado.
 *
 * <p>O usuário sempre é lido de {@link CurrentUser}, nunca de um id vindo da requisição. Aceitar
 * {@code PUT /users/{id}} aqui transformaria a tela de perfil em edição de qualquer pessoa do
 * escritório para quem trocasse o id na chamada — e a tela não mostraria diferença nenhuma.
 */
@Service
public class MyProfileService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public MyProfileService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public MyProfileDTO meusDados() {
        return toDTO(usuarioDaSessao());
    }

    @Transactional
    public MyProfileDTO atualizar(MyProfileUpdateRequestDTO dto) {
        User user = usuarioDaSessao();

        String email = dto.getEmail().trim();
        // O e-mail é credencial de login: duplicado, dois usuários passariam a disputar a mesma
        // entrada. O banco tem índice único e recusaria de qualquer forma - a checagem aqui é para
        // a resposta dizer QUAL campo está errado, em vez de um 500 de violação de constraint.
        repository
                .findByEmailIgnoreCase(email)
                .filter(outro -> !outro.getId().equals(user.getId()))
                .ifPresent(
                        outro -> {
                            throw new ValidationException(
                                    "email",
                                    ValidationErrorCode.DUPLICATE_VALUE,
                                    "Já existe um usuário com este e-mail no escritório.");
                        });

        user.setFullName(dto.getFullName().trim());
        user.setEmail(email);
        user.setUpdatedAt(Instant.now());
        return toDTO(repository.save(user));
    }

    @Transactional
    public void trocarSenha(ChangePasswordRequestDTO dto) {
        User user = usuarioDaSessao();

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            // Mensagem sobre a senha ATUAL, não sobre a nova: quem errou precisa saber onde.
            throw new ValidationException(
                    "currentPassword",
                    ValidationErrorCode.REQUIRED_FIELD,
                    "A senha atual não confere.");
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPasswordHash())) {
            // Trocar a senha pela mesma senha responde 200 e não troca nada. Quem fez isso porque
            // desconfia de acesso indevido sai achando que resolveu.
            throw new ValidationException(
                    "newPassword",
                    ValidationErrorCode.DUPLICATE_VALUE,
                    "A nova senha precisa ser diferente da atual.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        repository.save(user);
    }

    private User usuarioDaSessao() {
        UUID id = CurrentUser.id();
        if (id == null) {
            throw new NotFoundException("Não há usuário autenticado nesta requisição.");
        }
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "O usuário da sessão não existe mais neste escritório."));
    }

    private static MyProfileDTO toDTO(User user) {
        MyProfileDTO dto = new MyProfileDTO();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        dto.setActive(Boolean.TRUE.equals(user.getActive()));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }
}
