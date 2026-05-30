package com.eighthours.tickgo.controller;

import com.eighthours.tickgo.common.Result;
import com.eighthours.tickgo.dto.TicketPurchaseReqDTO;
import com.eighthours.tickgo.dto.TicketPurchaseRespDTO;
import com.eighthours.tickgo.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/query")
    public Result<TicketQueryRespDTO> query(@RequestParam Long trainId,
                                            @RequestParam String departure,
                                            @RequestParam String arrival) {
        TicketQueryRespDTO resp = ticketService.queryRemainTicket(trainId, departure, arrival);
        return Result.success(resp);
    }

    @PostMapping("/purchase")
    public Result<TicketPurchaseRespDTO> purchase(@RequestBody TicketPurchaseReqDTO requestParam) {
        TicketPurchaseRespDTO resp = ticketService.purchaseTicket(requestParam);
        return Result.success(resp);
    }

    @PostMapping("/token/init")
    public Result<Void> initToken(@RequestParam Long trainId,
                                  @RequestParam String departure,
                                  @RequestParam String arrival) {
        ticketService.initTicketToken(trainId, departure, arrival);
        return Result.success();
    }

}
