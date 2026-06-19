package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

@Data
public class SeatItemDTO {

    private Long passengerId;

    private Integer seatType;

    private String carriageNumber;

    private String seatNumber;

    private Integer amount;

}
