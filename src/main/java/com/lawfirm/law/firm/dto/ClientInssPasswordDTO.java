package com.lawfirm.law.firm.dto;

/**
 * A senha de acesso do cliente ao portal do INSS, devolvida só pelo endpoint dedicado.
 *
 * <p>É um DTO próprio, e não um campo em {@link ClientDetailsDTO}, porque o que o separa não é o
 * formato e sim a intenção: abrir a ficha de um cliente é rotina; abrir a senha dele é um ato que
 * precisa de papel autorizado e fica registrado na auditoria.
 */
public record ClientInssPasswordDTO(String inssPassword) {}
