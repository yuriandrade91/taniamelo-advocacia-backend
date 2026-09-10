package com.lawfirm.law.firm.audit;

/**
 * Ação registrada em {@link AuditLog}. Puramente técnica - não é exposta via label.
 *
 * <p>{@link #READ} é a exceção à regra "só mutação gera auditoria": ela existe para a leitura da
 * senha do INSS de um cliente. Consultar uma ficha é rotina e não vale registro; abrir a senha de
 * acesso ao INSS de outra pessoa é o tipo de ação que precisa ter nome, autor e data - inclusive
 * para o escritório se defender.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    READ
}
