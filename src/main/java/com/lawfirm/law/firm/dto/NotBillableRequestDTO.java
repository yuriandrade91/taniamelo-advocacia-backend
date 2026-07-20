package com.lawfirm.law.firm.dto;

import jakarta.validation.constraints.NotNull;

/** Corpo do endpoint de propósito único PATCH /clients/{id}/not-billable. */
public class NotBillableRequestDTO {

    @NotNull
    private Boolean notBillable;

    public Boolean getNotBillable() { return notBillable; }
    public void setNotBillable(Boolean notBillable) { this.notBillable = notBillable; }
}
