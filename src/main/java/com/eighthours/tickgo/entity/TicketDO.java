package com.eighthours.tickgo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ticket")
public class TicketDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private Long trainId;

    private String carriageNumber;

    private String seatNumber;

    private Long passengerId;

    private Integer seatType;

    private Integer ticketStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
