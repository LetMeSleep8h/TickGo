package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

@Data
public class TrainOptionDTO {

    private Long trainId;

    private String trainNumber;

    private String startStation;

    private String endStation;
}
