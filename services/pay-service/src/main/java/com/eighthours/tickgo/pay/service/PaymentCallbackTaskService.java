package com.eighthours.tickgo.pay.service;

public interface PaymentCallbackTaskService {

    void createCallbackTask(String paymentSn, String orderSn, String errorMsg);

    void retryTasks();
}
