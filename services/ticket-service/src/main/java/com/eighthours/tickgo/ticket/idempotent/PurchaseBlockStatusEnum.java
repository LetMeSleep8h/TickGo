package com.eighthours.tickgo.ticket.idempotent;

import lombok.Getter;

@Getter
public enum PurchaseBlockStatusEnum {

    ALLOW(0, null),
    WAIT_PAY(10, "乘车人存在待支付订单"),
    PAID(20, "乘车人已购票成功");

    private final int priority;
    private final String messagePrefix;

    PurchaseBlockStatusEnum(int priority, String messagePrefix) {
        this.priority = priority;
        this.messagePrefix = messagePrefix;
    }
}
