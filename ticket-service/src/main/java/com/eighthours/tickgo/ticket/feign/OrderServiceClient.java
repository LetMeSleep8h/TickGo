package com.eighthours.tickgo.ticket.feign;

import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.CreateTicketOrderReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", url = "http://localhost:8081")
public interface OrderServiceClient {

    @PostMapping("/order/create")
    Result<Void> createOrder(@RequestBody CreateTicketOrderReqDTO request);
}
