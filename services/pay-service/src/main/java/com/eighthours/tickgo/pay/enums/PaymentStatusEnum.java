package com.eighthours.tickgo.pay.enums;

import lombok.Getter;

@Getter
public enum PaymentStatusEnum {

    INIT(0, "待支付"),
    PAYING(10, "支付中"),
    SUCCESS(20, "支付成功"),
    FAILED(30, "支付失败"),
    CLOSED(40, "已关闭");

    private final Integer code;
    private final String desc;

    PaymentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
