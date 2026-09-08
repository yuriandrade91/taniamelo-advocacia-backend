package com.lawfirm.law.firm.dto;

import java.time.Instant;
import java.util.UUID;

/** Registro da trilha de alterações de um compromisso (edição/cancelamento com justificativa). */
public record AppointmentHistoryDTO(
        UUID id, String action, String justification, Instant changedAt, UUID changedByUserId) {}
