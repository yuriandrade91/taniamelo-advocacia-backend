package com.lawfirm.law.firm.model;

/**
 * Papéis de acesso ao sistema.
 *
 * <p>O corte é entre operar e destruir: STAFF faz o dia a dia inteiro (cadastra e edita cliente,
 * marca, cancela e conclui compromisso); excluir, restaurar e ler dado sensível exigem ADMIN ou
 * LAWYER. Quem impõe isso é {@link com.lawfirm.law.firm.security.RequerAdvogado} nos controllers,
 * não este enum — aqui ficam só os papéis que existem.
 */
public enum Role {
    ADMIN,
    LAWYER,
    STAFF
}
