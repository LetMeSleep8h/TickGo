package com.eighthours.tickgo.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderStatusRespDTO {

    private String orderSn;

    private Integer status;
}
