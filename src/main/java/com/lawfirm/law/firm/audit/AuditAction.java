package com.lawfirm.law.firm.audit;

/** Tipo de mutação registrada em {@link AuditLog}. Puramente técnico - não é exposto via label. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE
}
