package com.eighthours.tickgo.dto;

import lombok.Data;

import java.util.List;

@Data
public class TicketPurchaseReqDTO {

    private Long trainId;

    private String departure;

    private String arrival;

    private List<PassengerPurchaseReqDTO> passengers;

}
