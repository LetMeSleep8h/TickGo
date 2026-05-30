package com.eighthours.tickgo.controller;

import com.eighthours.tickgo.common.Result;
import com.eighthours.tickgo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/cancel")
    public Result<Void> cancelOrder(@RequestParam String orderSn) {
        orderService.cancelOrder(orderSn);
        return Result.success();
    }

    @PostMapping("/pay")
    public Result<Void> payOrder(@RequestParam String orderSn) {
        orderService.payOrder(orderSn);
        return Result.success();
    }
}
