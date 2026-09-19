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
    /**
     * Remoção - física (só {@code ClientAddress}) ou lógica.
     *
     * <p>Na exclusão lógica a linha continua no banco e, para o JPA, a operação é um update. Quem
     * diz que aquele update é uma remoção é o service, via {@link IntencaoDeAuditoria} - sem isso a
     * trilha registrava {@code UPDATE} e não sobrava registro de quem removeu o quê.
     */
    DELETE,
    /** Exclusão lógica desfeita. */
    RESTORE,
    READ
}
