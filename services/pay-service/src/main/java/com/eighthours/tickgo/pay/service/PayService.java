package com.eighthours.tickgo.pay.service;

import com.eighthours.tickgo.pay.dto.CreatePaymentRequestDTO;
import com.eighthours.tickgo.pay.dto.PaymentQueryRespDTO;

public interface PayService {

    PaymentQueryRespDTO createPayment(CreatePaymentRequestDTO request);

    PaymentQueryRespDTO submitPayment(String paymentSn);

    PaymentQueryRespDTO queryPayment(String paymentSn, String orderSn);

    void closePayment(String paymentSn);
}
