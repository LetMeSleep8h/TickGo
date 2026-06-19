package com.eighthours.tickgo.order.service;

import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;
import com.eighthours.tickgo.order.dto.OrderDetailRespDTO;
import com.eighthours.tickgo.order.dto.OrderStatusRespDTO;

import java.util.List;

public interface OrderService {

    void createOrder(CreateOrderRequestDTO request);

    void payOrder(String orderSn);

    void cancelOrder(String orderSn);

    void handlePayNotify(String paymentSn, String orderSn);

    OrderStatusRespDTO getOrderStatus(String orderSn);

    List<OrderDetailRespDTO> listOrders(Long userId);
}
