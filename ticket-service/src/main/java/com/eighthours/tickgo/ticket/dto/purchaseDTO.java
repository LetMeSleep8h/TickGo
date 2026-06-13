package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

@Data
public class purchaseDTO {

    private Long trainId;

    private String departure;

    private String arrival;

    private Integer seatType;
}
