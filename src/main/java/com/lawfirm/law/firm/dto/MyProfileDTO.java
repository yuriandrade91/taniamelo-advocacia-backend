package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
public class MyProfileDTO {

    private UUID id;
    private String fullName;
    private String email;
    private String username;

    @Schema(description = "Nome da constante (ADMIN, SUPPORT, STAFF) — somente leitura")
    private Role role;

    @Schema(description = "Somente leitura: desativar-se a si mesmo não é uma ação de perfil")
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;
}
