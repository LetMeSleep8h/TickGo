package com.eighthours.tickgo.ticket.controller;

import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.NewTicketOrderReqDTO;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.service.NewTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ticket/v2")
@RequiredArgsConstructor
public class NewTicketController {

    private final NewTicketService newTicketService;

    @GetMapping("/query")
    public Result<TicketQueryRespDTO> queryRemainTicket(@RequestParam Long trainId,
                                                        @RequestParam String departure,
                                                        @RequestParam String arrival) {
        return Result.success(newTicketService.queryRemainTicket(trainId, departure, arrival));
    }

    @PostMapping("/preOccupy")
    public Result<SeatPreOccupyRespDTO> purchaseTicketsV2(@RequestBody NewTicketPurchaseReqDTO request) {
        return Result.success(newTicketService.purchaseTicketsV2(request));
    }

    @PostMapping("/confirm")
    public Result<Void> confirmTickets(@RequestBody NewTicketOrderReqDTO request) {
        newTicketService.confirmTickets(request);
        return Result.success();
    }

    @PostMapping("/release")
    public Result<Void> releaseSeats(@RequestBody NewTicketOrderReqDTO request) {
        newTicketService.releaseSeats(request);
        return Result.success();
    }
}
