package com.eighthours.tickgo.ticket.service;

import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;

import java.util.List;

public interface TicketService {

    TicketQueryRespDTO queryRemainTicket(Long trainId, String departure, String arrival);

    void initTicketToken(Long trainId);

    SeatPreOccupyRespDTO preOccupySeats(Long trainId, String departure, String arrival, String orderSn,
                                       List<Long> passengerIds, List<Integer> seatTypes);

    void confirmTickets(String orderSn);

    void releaseSeats(String orderSn);
}
