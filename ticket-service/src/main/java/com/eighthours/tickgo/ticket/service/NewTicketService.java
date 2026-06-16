package com.eighthours.tickgo.ticket.service;

import com.eighthours.tickgo.ticket.dto.NewTicketOrderReqDTO;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;

public interface NewTicketService {

    TicketQueryRespDTO queryRemainTicket(Long trainId, String departure, String arrival);

    SeatPreOccupyRespDTO purchaseTicketsV2(NewTicketPurchaseReqDTO request);

    SeatPreOccupyRespDTO executePurchaseTickets(NewTicketPurchaseReqDTO request);

    void confirmTickets(NewTicketOrderReqDTO request);

    void releaseSeats(NewTicketOrderReqDTO request);
}
