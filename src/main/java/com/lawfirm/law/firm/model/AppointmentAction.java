package com.lawfirm.law.firm.model;

/**
 * Ação registrada em {@code appointment_history}. Puramente técnica (não exposta via label): toda
 * edição e todo cancelamento de um compromisso exigem justificativa e geram um registro.
 */
public enum AppointmentAction {
    EDITED,
    CANCELLED,
    /** Usuário confirmou ciência de que o compromisso está com data no passado (auditoria). */
    ACKNOWLEDGED
}
