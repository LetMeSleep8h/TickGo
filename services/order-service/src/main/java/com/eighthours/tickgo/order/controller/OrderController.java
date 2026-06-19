package com.eighthours.tickgo.order.controller;

import com.eighthours.tickgo.order.common.Result;
import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;
import com.eighthours.tickgo.order.dto.OrderDetailRespDTO;
import com.eighthours.tickgo.order.dto.OrderStatusRespDTO;
import com.eighthours.tickgo.order.dto.PayNotifyRequestDTO;
import com.eighthours.tickgo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/status")
    public Result<OrderStatusRespDTO> getOrderStatus(@RequestParam String orderSn) {
        return Result.success(orderService.getOrderStatus(orderSn));
    }

    @GetMapping("/list")
    public Result<List<OrderDetailRespDTO>> listOrders(@RequestParam Long userId) {
        return Result.success(orderService.listOrders(userId));
    }

    @PostMapping("/pay-notify")
    public Result<Void> payNotify(@RequestBody PayNotifyRequestDTO request) {
        orderService.handlePayNotify(request.getPaymentSn(), request.getOrderSn());
        return Result.success();
    }
}
