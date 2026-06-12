package com.nana.aiarticlestudio.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {

    private int code;

    private String message;

    private T data;

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, "ok", data);
    }

    public static <T> BaseResponse<T> fail(String message) {
        return new BaseResponse<>(500, message, null);
    }
}