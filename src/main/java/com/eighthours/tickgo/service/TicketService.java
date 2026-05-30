package com.eighthours.tickgo.service;

import com.eighthours.tickgo.dto.TicketPurchaseReqDTO;
import com.eighthours.tickgo.dto.TicketPurchaseRespDTO;
import com.eighthours.tickgo.dto.TicketQueryRespDTO;

public interface TicketService {

    TicketQueryRespDTO queryRemainTicket(Long trainId, String departure, String arrival);

    void initTicketToken(Long trainId, String departure, String arrival);

    TicketPurchaseRespDTO purchaseTicket(TicketPurchaseReqDTO requestParam);

}
