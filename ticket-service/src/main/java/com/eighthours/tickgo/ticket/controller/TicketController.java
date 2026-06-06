package com.eighthours.tickgo.ticket.controller;

import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/query")
    public Result<Map<String, Object>> queryRemainTicket(@RequestParam Long trainId,
                                                         @RequestParam String departure,
                                                         @RequestParam String arrival) {
        Map<String, Object> data = ticketService.queryRemainTicket(trainId, departure, arrival);
        return Result.success(data);
    }

    @PostMapping("/preOccupy")
    public Result<Map<String, Object>> preOccupySeats(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> passengers = (List<Map<String, Object>>) request.get("passengers");
        List<Long> passengerIds = passengers.stream()
                .map(p -> ((Number) p.get("passengerId")).longValue())
                .toList();
        List<Integer> seatTypes = passengers.stream()
                .map(p -> ((Number) p.get("seatType")).intValue())
                .toList();

        Map<String, Object> data = ticketService.preOccupySeats(
                ((Number) request.get("trainId")).longValue(),
                (String) request.get("departure"),
                (String) request.get("arrival"),
                (String) request.get("orderSn"),
                passengerIds,
                seatTypes);
        return Result.success(data);
    }

    @PostMapping("/confirm")
    public Result<Void> confirmTickets(@RequestParam String orderSn) {
        ticketService.confirmTickets(orderSn);
        return Result.success();
    }

    @PostMapping("/release")
    public Result<Void> releaseSeats(@RequestParam String orderSn) {
        ticketService.releaseSeats(orderSn);
        return Result.success();
    }

    @PostMapping("/initToken")
    public Result<Void> initTicketToken(@RequestParam Long trainId,
                                        @RequestParam String departure,
                                        @RequestParam String arrival) {
        ticketService.initTicketToken(trainId, departure, arrival);
        return Result.success();
    }
}
