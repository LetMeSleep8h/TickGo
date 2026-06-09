package com.eighthours.tickgo.order.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderCancelDelayMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderSn;
}
