package com.lawfirm.law.firm.audit;

import java.util.function.Supplier;

/**
 * A ação que o service está executando, para o {@link AuditLogListener} não ter de adivinhar.
 *
 * <p>Existe por um defeito verificado no banco: {@code DELETE} só aparecia para {@code
 * ClientAddress}, a única entidade com exclusão física. Todas as outras "excluem" gravando {@code
 * deleted_at} e salvando - o que, para o JPA, é um {@code @PostUpdate} como qualquer outro.
 * Cliente, compromisso, parcela, arquivo e entrevista excluídos ficavam registrados como {@code
 * UPDATE}, indistinguíveis de uma correção de telefone.
 *
 * <p>Isso derrubava o motivo pelo qual a tabela foi criada, escrito na própria {@code
 * V8__audit_log.sql}: "sem essa trilha à parte, não sobra nenhum registro de quem removeu o quê".
 *
 * <p><b>Por que não deduzir no listener.</b> Os callbacks do JPA ({@code @PreUpdate},
 * {@code @PostUpdate}) só enxergam o estado NOVO da entidade - quando eles rodam, o {@code
 * deletedAt} já foi preenchido e não há com o que comparar. Distinguir ali exigiria um {@code
 * Interceptor} do Hibernate recebendo estado anterior e atual, o que é muito maquinário para uma
 * informação que o service já tem em mãos na hora de chamar {@code save}.
 *
 * <p><b>Use {@code saveAndFlush}, não {@code save}, dentro do bloco.</b> Em entidade gerenciada o
 * {@code save} não força flush, e sem flush o {@code @PostUpdate} só dispara no commit - depois do
 * {@code finally} ter limpado a intenção. O registro sairia como {@code UPDATE} de novo, e o teste
 * de integração é que pega isso.
 *
 * <p>ThreadLocal pelo mesmo motivo de {@code TenantContext} e {@code CurrentUser}: é contexto da
 * requisição que precisa atravessar camadas sem virar parâmetro em cada assinatura. Sempre com
 * {@code try/finally}, para uma exceção não deixar a intenção grudada na thread e contaminar a
 * próxima requisição que ela atender.
 */
public final class IntencaoDeAuditoria {

    private static final ThreadLocal<AuditAction> ATUAL = new ThreadLocal<>();

    private IntencaoDeAuditoria() {}

    /** Executa a operação declarando a ação que ela representa na trilha. */
    public static <T> T declarando(AuditAction acao, Supplier<T> operacao) {
        AuditAction anterior = ATUAL.get();
        ATUAL.set(acao);
        try {
            return operacao.get();
        } finally {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
        }
    }

    /** Mesma coisa, para operação sem retorno. */
    public static void declarando(AuditAction acao, Runnable operacao) {
        declarando(
                acao,
                () -> {
                    operacao.run();
                    return null;
                });
    }

    /** A ação declarada, ou {@code null} quando ninguém declarou nada (o caso comum). */
    static AuditAction atual() {
        return ATUAL.get();
    }
}
