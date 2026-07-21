package com.lawfirm.law.firm.dto;

/**
 * Atualização parcial do cliente (PATCH /clients/{id}): situação, benefício e/ou arrecadação. Envie
 * só os campos que quer mudar - null significa "não mexer nisso". situation/benefit aceitam o nome
 * do enum ou o label PT-BR (validado no service para produzir erro amigável).
 */
public class ClientPatchRequestDTO {

    private String situation;
    private String benefit;
    private Boolean notBillable;

    public String getSituation() {
        return situation;
    }

    public void setSituation(String situation) {
        this.situation = situation;
    }

    public String getBenefit() {
        return benefit;
    }

    public void setBenefit(String benefit) {
        this.benefit = benefit;
    }

    public Boolean getNotBillable() {
        return notBillable;
    }

    public void setNotBillable(Boolean notBillable) {
        this.notBillable = notBillable;
    }
}
