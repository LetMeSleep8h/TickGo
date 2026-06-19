package com.eighthours.tickgo.ticket.controller;

import com.eighthours.tickgo.ticket.dto.PreOccupyRequestDTO;
import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.dto.TrainOptionDTO;
import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.purchaseDTO;
import com.eighthours.tickgo.ticket.entity.SeatDO;
import com.eighthours.tickgo.ticket.exception.BizException;
import com.eighthours.tickgo.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/query")
    public Result<TicketQueryRespDTO> queryRemainTicket(@RequestParam Long trainId,
                                                         @RequestParam String departure,
                                                         @RequestParam String arrival) {
        TicketQueryRespDTO data = ticketService.queryRemainTicket(trainId, departure, arrival);
        return Result.success(data);
    }

    @GetMapping("/meta/trains")
    public Result<List<TrainOptionDTO>> listAvailableTrains() {
        return Result.success(ticketService.listAvailableTrains());
    }

    @GetMapping("/meta/stations")
    public Result<List<String>> listTrainStations(@RequestParam Long trainId) {
        return Result.success(ticketService.listTrainStations(trainId));
    }

    @PostMapping("/purchaseV1")
    public SeatDO purchaseV1(@RequestBody purchaseDTO request) {
        return ticketService.purchaseV1(request);
    }


    @PostMapping("/preOccupy")
    public Result<SeatPreOccupyRespDTO> preOccupySeats(@RequestBody PreOccupyRequestDTO request) {
        if (Objects.isNull(request)) {
            throw new BizException("乘车人不能为空");
        }
        if (Objects.isNull(request.getPassengers()) || request.getPassengers().isEmpty()) {
            throw new BizException("乘车人不能为空");
        }
        List<Long> passengerIds = request.getPassengers().stream()
                .map(PreOccupyRequestDTO.PassengerSeatDTO::getPassengerId)
                .toList();
        List<Integer> seatTypes = request.getPassengers().stream()
                .map(PreOccupyRequestDTO.PassengerSeatDTO::getSeatType)
                .toList();

        SeatPreOccupyRespDTO data = ticketService.preOccupySeats(
                request.getTrainId(),
                request.getDeparture(),
                request.getArrival(),
                request.getOrderSn(),
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
                                        @RequestParam(required = false) String departure,
                                        @RequestParam(required = false) String arrival) {
        ticketService.initTicketToken(trainId);
        return Result.success();
    }
}
