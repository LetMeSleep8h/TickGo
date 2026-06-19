package com.eighthours.tickgo.pay.enums;

import lombok.Getter;

@Getter
public enum CallbackStatusEnum {

    PENDING(0, "待回调"),
    SUCCESS(10, "回调成功"),
    FAILED(20, "回调失败");

    private final Integer code;
    private final String desc;

    CallbackStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
