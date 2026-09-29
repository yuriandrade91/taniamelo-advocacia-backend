package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Os próprios dados de quem está autenticado.
 *
 * <p>Traz o e-mail, que {@link UserSummaryDTO} não traz. Não é contradição: aquela lista serve para
 * traduzir id em nome e é lida por terceiros — ali o e-mail seria um diretório de contatos que
 * ninguém pediu. Aqui a pessoa está vendo a própria ficha.
 *
 * <p>Não traz o hash da senha, nem o estado de bloqueio por tentativas. Papel e situação vêm como
 * leitura: quem muda papel é quem administra, não o próprio usuário.
 */
public class MyProfileDTO {

    private UUID id;
    private String fullName;
    private String email;
    private String username;

    @Schema(description = "Nome da constante (ADMIN, LAWYER, STAFF) — somente leitura")
    private Role role;

    @Schema(description = "Somente leitura: desativar-se a si mesmo não é uma ação de perfil")
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
