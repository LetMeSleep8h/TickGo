package com.eighthours.tickgo.service;

public interface OrderService {

    void cancelOrder(String orderSn);

    void payOrder(String orderSn);

}
