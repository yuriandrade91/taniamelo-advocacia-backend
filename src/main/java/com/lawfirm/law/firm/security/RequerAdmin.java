package com.lawfirm.law.firm.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Restringe a operação a ADMIN.
 *
 * <p>Mais estrita que {@link RequerAdvogado}: ali o corte é entre operar e destruir, aqui é
 * dinheiro. Honorários - quanto foi combinado, o que já entrou, o que está vencido - são do
 * escritório, não do caso. Quem advoga no processo não precisa disso para trabalhar, e não é por
 * desconfiança: informação financeira que circula por conveniência acaba circulando por acidente.
 *
 * <p>Anotação própria, e não {@code @PreAuthorize} repetido, pela mesma razão do {@code
 * RequerAdvogado}: mudar quem vê o financeiro é editar este arquivo.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasRole('ADMIN')")
public @interface RequerAdmin {}
