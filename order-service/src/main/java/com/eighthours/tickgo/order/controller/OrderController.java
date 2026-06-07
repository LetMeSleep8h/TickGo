package com.eighthours.tickgo.order.controller;

import com.eighthours.tickgo.order.common.Result;
import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;
import com.eighthours.tickgo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<Void> createOrder(@RequestBody CreateOrderRequestDTO request) {
        orderService.createOrder(request);
        return Result.success();
    }

    @PostMapping("/pay")
    public Result<Void> payOrder(@RequestParam String orderSn) {
        orderService.payOrder(orderSn);
        return Result.success();
    }

    @PostMapping("/cancel")
    public Result<Void> cancelOrder(@RequestParam String orderSn) {
        orderService.cancelOrder(orderSn);
        return Result.success();
    }
}
