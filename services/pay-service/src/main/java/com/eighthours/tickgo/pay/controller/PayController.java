package com.eighthours.tickgo.pay.controller;

import com.eighthours.tickgo.pay.common.Result;
import com.eighthours.tickgo.pay.dto.CreatePaymentRequestDTO;
import com.eighthours.tickgo.pay.dto.PaymentQueryRespDTO;
import com.eighthours.tickgo.pay.dto.SubmitPaymentRequestDTO;
import com.eighthours.tickgo.pay.service.PayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    @PostMapping("/create")
    public Result<PaymentQueryRespDTO> create(@RequestBody CreatePaymentRequestDTO request) {
        return Result.success(payService.createPayment(request));
    }

    @PostMapping("/submit")
    public Result<PaymentQueryRespDTO> submit(@RequestBody SubmitPaymentRequestDTO request) {
        return Result.success(payService.submitPayment(request.getPaymentSn()));
    }

    @GetMapping("/status")
    public Result<PaymentQueryRespDTO> status(@RequestParam(required = false) String paymentSn,
                                              @RequestParam(required = false) String orderSn) {
        return Result.success(payService.queryPayment(paymentSn, orderSn));
    }

    @PostMapping("/close")
    public Result<Void> close(@RequestParam String paymentSn) {
        payService.closePayment(paymentSn);
        return Result.success();
    }
}
