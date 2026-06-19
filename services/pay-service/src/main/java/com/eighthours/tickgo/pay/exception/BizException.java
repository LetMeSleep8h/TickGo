package com.eighthours.tickgo.pay.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String message) {
        super(message);
        this.code = "BIZ_ERROR";
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}
