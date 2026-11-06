package com.lawfirm.law.firm.tenant;

/**
 * Guarda o tenant (schema) da requisição corrente numa ThreadLocal. Populado pelo {@link
 * TenantResolutionFilter} no início da requisição e lido pelo {@code SchemaCurrentTenantResolver}
 * quando o Hibernate abre a conexão. Sempre limpo no fim da requisição para não vazar entre threads
 * reaproveitadas do pool do servlet.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantSchema) {
        CURRENT.set(tenantSchema);
    }

    /** Schema do tenant corrente, ou {@code null} se não definido (cai no default no resolver). */
    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
