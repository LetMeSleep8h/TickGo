package com.eighthours.tickgo.order.controller;

import com.eighthours.tickgo.order.common.Result;
import com.eighthours.tickgo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<Void> createOrder(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");

        orderService.createOrder(
                (String) request.get("orderSn"),
                ((Number) request.get("userId")).longValue(),
                (String) request.get("username"),
                ((Number) request.get("trainId")).longValue(),
                (String) request.get("trainNumber"),
                (String) request.get("departure"),
                (String) request.get("arrival"),
                items);
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
