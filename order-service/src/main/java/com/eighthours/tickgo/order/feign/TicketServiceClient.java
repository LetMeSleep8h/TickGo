package com.eighthours.tickgo.order.feign;

import com.eighthours.tickgo.order.dto.TicketOrderRequestDTO;
import com.eighthours.tickgo.order.ticket.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ticket-service", url = "http://localhost:8082")
public interface TicketServiceClient {

    @PostMapping("/ticket/v2/confirm")
    Result<Void> confirmTickets(@RequestBody TicketOrderRequestDTO request);

    @PostMapping("/ticket/v2/release")
    Result<Void> releaseSeats(@RequestBody TicketOrderRequestDTO request);

}
