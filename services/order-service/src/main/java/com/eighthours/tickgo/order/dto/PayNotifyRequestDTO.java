package com.eighthours.tickgo.order.dto;

import lombok.Data;

@Data
public class PayNotifyRequestDTO {

    private String paymentSn;

    private String orderSn;
}
