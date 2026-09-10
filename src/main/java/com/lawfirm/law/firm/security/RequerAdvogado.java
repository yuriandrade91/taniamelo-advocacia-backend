package com.lawfirm.law.firm.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Restringe a operação a ADMIN e LAWYER.
 *
 * <p>É a linha entre operar e destruir. STAFF - secretaria, estágio - cadastra e edita cliente e
 * compromisso, cancela e conclui agenda: o dia a dia inteiro. O que fica de fora é o que não tem
 * volta fácil ou não é da conta de quem opera: excluir, restaurar, ler a senha do INSS de um
 * cliente e ver a lista de usuários do escritório.
 *
 * <p>Existe como anotação própria, e não como {@code @PreAuthorize} repetido em cada método, para
 * que a regra tenha um lugar: mudar quem pode excluir é editar este arquivo, não caçar quinze
 * expressões SpEL espalhadas. O {@code SecuredEndpointsContractTest} garante que nenhum {@code
 * DELETE} novo nasça sem ela.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyRole('ADMIN','LAWYER')")
public @interface RequerAdvogado {}
