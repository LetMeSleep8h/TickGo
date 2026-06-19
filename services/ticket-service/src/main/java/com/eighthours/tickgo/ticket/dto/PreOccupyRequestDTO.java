package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

import java.util.List;

@Data
public class PreOccupyRequestDTO {

    private Long trainId;

    private String departure;

    private String arrival;

    private String orderSn;

    private List<PassengerSeatDTO> passengers;

    @Data
    public static class PassengerSeatDTO {
        private Long passengerId;
        private Integer seatType;
    }
}
