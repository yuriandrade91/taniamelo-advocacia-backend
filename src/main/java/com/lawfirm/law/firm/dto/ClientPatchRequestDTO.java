package com.lawfirm.law.firm.dto;

import com.lawfirm.law.firm.model.Situation;

public class ClientPatchRequestDTO {
    private Situation situation;
    private Boolean nonBillable;

    public ClientPatchRequestDTO() {
    }

    public Situation getSituation() {
        return situation;
    }

    public void setSituation(Situation situation) {
        this.situation = situation;
    }

    public Boolean getNonBillable() {
        return nonBillable;
    }

    public void setNonBillable(Boolean nonBillable) {
        this.nonBillable = nonBillable;
    }
}
