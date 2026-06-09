package com.eighthours.tickgo.ticket.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeatPreOccupyRespDTO {

    private String trainNumber;

    private List<SeatItemDTO> items;

}
