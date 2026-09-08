package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.UserSummaryDTO;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usuários do escritório, somente leitura.
 *
 * <p>Existe para fechar uma lacuna simples e incômoda: o banco guarda quem criou, quem alterou e
 * quem é responsável, sempre como UUID, e não havia nada que traduzisse esses ids em nome. Toda
 * tela que quisesse dizer "alterado por Fulano" só conseguia mostrar um UUID.
 *
 * <p>Deliberadamente sem paginação: um escritório tem uma dezena de usuários e a tela carrega a
 * lista uma vez para resolver todos os ids da página. Paginar aqui obrigaria o front a buscar de
 * novo a cada id não encontrado - mais requisições para servir menos dado.
 *
 * <p>Criar, editar e desativar usuário são ações de administração e dependem de autorização por
 * papel; ficam para quando ela existir (ver ROADMAP).
 */
@Tag(name = "Usuários", description = "Usuários do escritório - consulta para resolver autoria")
@SecurityRequirements({
    @SecurityRequirement(name = "bearerAuth"),
    @SecurityRequirement(name = "tenantHeader")
})
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(
            summary = "Listar usuários do escritório",
            description =
                    """
                    Lista completa, ordenada por nome, no envelope padrão e sem paginação.

                    Serve para traduzir em nome os ids que os demais recursos devolvem: \
                    `createdBy`, `updatedBy`, `responsibleUserId` e o `changedByUserId` do \
                    histórico de situação do cliente.

                    Por padrão traz só os **ativos** - um usuário desativado não deve aparecer em \
                    seletor de responsável. Passe `includeInactive=true` para resolver o nome de \
                    quem já saiu do escritório mas assina registros antigos.

                    Nenhuma credencial ou e-mail é exposto.""")
    @GetMapping
    public ResponseEntity<ApiResponse<UserSummaryDTO>> list(
            @Parameter(description = "Inclui usuários desativados (para resolver autoria antiga)")
                    @RequestParam(defaultValue = "false")
                    boolean includeInactive) {
        List<UserSummaryDTO> users =
                userRepository.findAll(Sort.by(Sort.Direction.ASC, "fullName")).stream()
                        .filter(user -> includeInactive || Boolean.TRUE.equals(user.getActive()))
                        .map(UserController::toDTO)
                        .toList();
        return ResponseEntity.ok(ApiResponse.successList(users));
    }

    private static UserSummaryDTO toDTO(User user) {
        return new UserSummaryDTO(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getActive()));
    }
}
