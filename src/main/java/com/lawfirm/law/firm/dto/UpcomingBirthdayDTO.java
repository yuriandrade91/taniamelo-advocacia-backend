package com.lawfirm.law.firm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** Linha do card "próximos aniversariantes" da Home. */
public record UpcomingBirthdayDTO(
        @Schema(description = "Id do cliente") UUID clientId,
        @Schema(description = "Nome completo") String fullName,
        @Schema(description = "Data de nascimento", example = "1970-10-12") LocalDate birthDate,
        @Schema(description = "Próxima data de aniversário", example = "2026-10-12")
                LocalDate nextBirthday,
        @Schema(description = "Dias até o aniversário (0 = hoje)", example = "5") long daysUntil,
        @Schema(description = "Idade que completa", example = "56") int turningAge) {}
