package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Role;
import java.util.UUID;

/**
 * Usuário do escritório, no mínimo necessário para resolver "por quem".
 *
 * <p>Vários recursos gravam apenas o UUID de quem agiu - {@code createdBy}, {@code updatedBy},
 * {@code changedByUserId} do histórico de situação, {@code responsibleUserId} do cliente. Sem um
 * lugar que traduza esse id em nome, a informação existe no banco e não é exibível em lugar nenhum.
 *
 * <p>Não expõe e-mail nem qualquer credencial: o propósito é tradução de id para nome, não um
 * diretório de contatos.
 */
public record UserSummaryDTO(
        UUID id, String fullName, String username, Role role, boolean active) {}
