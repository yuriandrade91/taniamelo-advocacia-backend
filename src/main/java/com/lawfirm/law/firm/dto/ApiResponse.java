package com.lawfirm.law.firm.dto;

import java.util.List;

public class ApiResponse<T> {
    private boolean success;
    private Object data; // can be either List<T> or single T
    private Pagination pagination;
    private List<ApiError> errors;

    public ApiResponse() {}

    public static <T> ApiResponse<T> successList(List<T> data, Pagination pagination) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        r.pagination = pagination;
        return r;
    }

    public static <T> ApiResponse<T> successList(List<T> data) {
        return successList(data, null);
    }

    public static <T> ApiResponse<T> successObject(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(List<ApiError> errors) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.data = List.of();
        r.errors = errors;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public Pagination getPagination() { return pagination; }
    public void setPagination(Pagination pagination) { this.pagination = pagination; }

    public List<ApiError> getErrors() { return errors; }
    public void setErrors(List<ApiError> errors) { this.errors = errors; }
}
