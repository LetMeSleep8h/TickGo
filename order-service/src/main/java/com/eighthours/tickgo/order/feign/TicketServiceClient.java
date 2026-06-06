package com.eighthours.tickgo.order.feign;

import com.eighthours.tickgo.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ticket-service", url = "http://localhost:8082")
public interface TicketServiceClient {

    @PostMapping("/ticket/confirm")
    Result<Void> confirmTickets(@RequestParam("orderSn") String orderSn);

    @PostMapping("/ticket/release")
    Result<Void> releaseSeats(@RequestParam("orderSn") String orderSn);

}
