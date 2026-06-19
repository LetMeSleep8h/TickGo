package com.eighthours.tickgo.pay.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentQueryRespDTO {

    private String paymentSn;

    private String orderSn;

    private Integer status;

    private Integer callbackStatus;

    private Integer payAmount;

    private LocalDateTime successTime;
}
