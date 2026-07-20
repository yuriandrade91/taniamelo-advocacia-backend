package com.lawfirm.law.firm.dto;

/**
 * Atualização parcial do cliente (PATCH /clients/{id}): situação e/ou
 * arrecadação. situation aceita o nome do enum ou o label PT-BR (validado no
 * service para produzir erro amigável).
 */
public class ClientPatchRequestDTO {

    private String situation;
    private Boolean notBillable;

    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public Boolean getNotBillable() { return notBillable; }
    public void setNotBillable(Boolean notBillable) { this.notBillable = notBillable; }
}
