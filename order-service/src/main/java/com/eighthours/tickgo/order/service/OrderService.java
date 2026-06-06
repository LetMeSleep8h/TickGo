package com.eighthours.tickgo.order.service;

import java.util.List;
import java.util.Map;

public interface OrderService {

    void createOrder(String orderSn, Long userId, String username, Long trainId, String trainNumber,
                     String departure, String arrival, List<Map<String, Object>> items);

    void payOrder(String orderSn);

    void cancelOrder(String orderSn);
}
