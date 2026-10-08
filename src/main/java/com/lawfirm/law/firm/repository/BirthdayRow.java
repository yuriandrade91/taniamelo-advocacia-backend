package com.lawfirm.law.firm.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção mínima para o card de aniversariantes: só o que a tela mostra. Evita carregar a entidade
 * inteira (e descriptografar a senha do INSS) para cada linha.
 */
public record BirthdayRow(UUID clientId, String fullName, LocalDate birthDate) {}
