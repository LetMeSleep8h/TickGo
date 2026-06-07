package com.eighthours.tickgo.order.service;

import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;

public interface OrderService {

    void createOrder(CreateOrderRequestDTO request);

    void payOrder(String orderSn);

    void cancelOrder(String orderSn);
}
