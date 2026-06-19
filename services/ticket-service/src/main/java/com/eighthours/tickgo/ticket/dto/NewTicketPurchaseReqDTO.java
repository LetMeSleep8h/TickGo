package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

import java.util.List;

@Data
public class NewTicketPurchaseReqDTO {

    private Long userId;

    private Long trainId;

    private String departure;

    private String arrival;

    private String orderSn;

    private List<PassengerDTO> passengers;

    @Data
    public static class PassengerDTO {
        private Long passengerId;
        private Integer seatType;
    }
}
