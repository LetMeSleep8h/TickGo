package com.eighthours.tickgo.ticket.service;

import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.dto.TrainOptionDTO;
import com.eighthours.tickgo.ticket.dto.purchaseDTO;
import com.eighthours.tickgo.ticket.entity.SeatDO;

import java.util.List;

public interface TicketService {

    SeatDO purchaseV1(purchaseDTO request);


    TicketQueryRespDTO queryRemainTicket(Long trainId, String departure, String arrival);

    void initTicketToken(Long trainId);

    SeatPreOccupyRespDTO preOccupySeats(Long trainId, String departure, String arrival, String orderSn,
                                       List<Long> passengerIds, List<Integer> seatTypes);

    void confirmTickets(String orderSn);

    void releaseSeats(String orderSn);

    List<TrainOptionDTO> listAvailableTrains();

    List<String> listTrainStations(Long trainId);
}
