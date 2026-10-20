package com.lawfirm.law.firm.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiError {
    private String field;
    private String message;
    private String code;

    public ApiError() {}

    public ApiError(String field, String message, String code) {
        this.field = field;
        this.message = message;
        this.code = code;
    }
}
