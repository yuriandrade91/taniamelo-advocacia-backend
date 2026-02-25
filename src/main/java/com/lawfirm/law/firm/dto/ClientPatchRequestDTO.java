package com.lawfirm.law.firm.dto;

public class ClientPatchRequestDTO {
    // accept raw string from frontend (enum name or label); service will convert to Situation
    private String situation;
    private Boolean nonBillable;

    public ClientPatchRequestDTO() {
    }

    public String getSituation() {
        return situation;
    }

    public void setSituation(String situation) {
        this.situation = situation;
    }

    public Boolean getNonBillable() {
        return nonBillable;
    }

    public void setNonBillable(Boolean nonBillable) {
        this.nonBillable = nonBillable;
    }
}
