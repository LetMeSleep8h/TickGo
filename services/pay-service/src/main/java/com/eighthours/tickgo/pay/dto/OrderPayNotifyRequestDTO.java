package com.eighthours.tickgo.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPayNotifyRequestDTO {

    private String paymentSn;

    private String orderSn;
}
