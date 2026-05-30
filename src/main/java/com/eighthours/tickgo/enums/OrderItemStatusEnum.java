package com.eighthours.tickgo.enums;

import lombok.Getter;

@Getter
public enum OrderItemStatusEnum {

    WAIT_PAY(0, "待支付"),
    PAID(10, "已支付"),
    CANCELED(30, "已取消");

    private final Integer code;
    private final String desc;

    OrderItemStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
