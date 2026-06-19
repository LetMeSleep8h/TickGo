package com.eighthours.tickgo.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailRespDTO {

    private String orderSn;

    private Long userId;

    private String username;

    private Long trainId;

    private String trainNumber;

    private String departure;

    private String arrival;

    private Integer status;

    private LocalDateTime orderTime;

    private LocalDateTime expireTime;

    private List<OrderItemRespDTO> items;

    @Data
    public static class OrderItemRespDTO {

        private String passengerName;

        private Integer seatType;

        private String carriageNumber;

        private String seatNumber;

        private Integer amount;
    }
}
