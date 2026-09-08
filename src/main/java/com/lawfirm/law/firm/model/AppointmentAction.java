package com.lawfirm.law.firm.model;

/**
 * Ação registrada em {@code appointment_history}. Puramente técnica (não exposta via label).
 *
 * <p>{@link #EDITED} e {@link #CANCELLED} exigem justificativa (validada no {@code
 * AppointmentService}); {@link #DELETED} e {@link #RESTORED} são registradas sem texto - o que
 * importa nelas é quem fez e quando.
 */
public enum AppointmentAction {
    EDITED,
    CANCELLED,
    /** Usuário confirmou ciência de que o compromisso está com data no passado (auditoria). */
    ACKNOWLEDGED,
    /** Exclusão lógica (soft delete). */
    DELETED,
    /** Exclusão desfeita. */
    RESTORED
}
