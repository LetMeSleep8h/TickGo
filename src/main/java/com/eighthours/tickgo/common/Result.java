package com.eighthours.tickgo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private String code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>("0", "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>("0", "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>("0", message, data);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>("BIZ_ERROR", message, null);
    }
}
