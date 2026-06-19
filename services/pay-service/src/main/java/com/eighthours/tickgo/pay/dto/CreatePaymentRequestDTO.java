package com.eighthours.tickgo.pay.dto;

import lombok.Data;

@Data
public class CreatePaymentRequestDTO {

    private String orderSn;

    private Long userId;

    private Integer payAmount;
}
