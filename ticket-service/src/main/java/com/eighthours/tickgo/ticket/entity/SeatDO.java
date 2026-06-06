package com.eighthours.tickgo.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_seat")
public class SeatDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long trainId;

    private String carriageNumber;

    private String seatNumber;

    private Integer seatType;

    private String startStation;

    private String endStation;

    private Integer startSequence;

    private Integer endSequence;

    private Integer price;

    private Integer seatStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
