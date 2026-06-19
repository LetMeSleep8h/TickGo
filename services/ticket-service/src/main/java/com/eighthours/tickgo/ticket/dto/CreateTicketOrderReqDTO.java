package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateTicketOrderReqDTO {

    private String orderSn;

    private Long userId;

    private String username;

    private Long trainId;

    private String trainNumber;

    private String departure;

    private String arrival;

    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {
        private Long passengerId;
        private Integer seatType;
        private String carriageNumber;
        private String seatNumber;
        private Integer amount;
    }
}
