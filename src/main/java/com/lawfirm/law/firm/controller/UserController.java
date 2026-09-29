package com.lawfirm.law.firm.controller;

import com.lawfirm.law.firm.dto.ApiResponse;
import com.lawfirm.law.firm.dto.ChangePasswordRequestDTO;
import com.lawfirm.law.firm.dto.MyProfileDTO;
import com.lawfirm.law.firm.dto.MyProfileUpdateRequestDTO;
import com.lawfirm.law.firm.dto.UserSummaryDTO;
import com.lawfirm.law.firm.model.User;
import com.lawfirm.law.firm.repository.UserRepository;
import com.lawfirm.law.firm.security.RequerAdvogado;
import com.lawfirm.law.firm.service.MyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * <p>Listar exige ADMIN ou LAWYER ({@code @RequerAdvogado}): quem trabalha no escritório não é da
 * conta de quem só opera.
 *
 * <p>Criar, editar e desativar usuário continuam sem endpoint - hoje o único caminho é o {@code
 * AdminUserSeeder}, que sempre escreve ADMIN. Ver ROADMAP.
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
    private final MyProfileService myProfileService;

    public UserController(UserRepository userRepository, MyProfileService myProfileService) {
        this.userRepository = userRepository;
        this.myProfileService = myProfileService;
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
    @RequerAdvogado
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

    // ── Meus dados ──
    //
    // Sem @RequerAdvogado, e é deliberado: são os DADOS DA PRÓPRIA PESSOA. Exigir papel aqui
    // deixaria o atendente sem tela de perfil e sem como trocar a própria senha — que é
    // exatamente quem mais precisa trocar, por ser a conta mais usada no balcão.
    //
    // O usuário vem de CurrentUser, nunca de um id na URL. Não existe `/users/{id}` para edição:
    // com ele, trocar o id na chamada viraria edição de qualquer pessoa do escritório, e a tela
    // não mostraria diferença nenhuma.

    @Operation(
            summary = "Meus dados",
            description =
                    """
                    Os dados de quem está autenticado. Traz o e-mail, que a listagem de usuários                     não traz — ali seria um diretório de contatos; aqui a pessoa está vendo a                     própria ficha.

                    Papel e situação vêm como leitura: quem muda papel é quem administra.""")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileDTO>> meusDados() {
        return ResponseEntity.ok(ApiResponse.successObject(myProfileService.meusDados()));
    }

    @Operation(
            summary = "Atualizar meus dados",
            description =
                    """
                    Nome e e-mail. E-mail repetido no escritório responde 400 nomeando o campo,                     em vez do erro de constraint do banco.

                    Papel, situação e nome de usuário não entram: papel seria escalada de                     privilégio disfarçada de edição de perfil, e o nome de usuário é a credencial                     de entrada.""")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileDTO>> atualizarMeusDados(
            @Valid @RequestBody MyProfileUpdateRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.successObject(myProfileService.atualizar(dto)));
    }

    @Operation(
            summary = "Trocar minha senha",
            description =
                    """
                    Exige a senha atual mesmo com a sessão autenticada: o token dura horas e a                     máquina fica destravada — sem a confirmação, quem passar por um computador                     aberto toma a conta.

                    A nova senha precisa ser diferente da atual. Trocar pela mesma responderia 200                     sem trocar nada, e quem fez isso por desconfiar de acesso indevido sairia                     achando que resolveu.""")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> trocarMinhaSenha(@Valid @RequestBody ChangePasswordRequestDTO dto) {
        myProfileService.trocarSenha(dto);
        return ResponseEntity.noContent().build();
    }
}
