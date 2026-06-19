package com.eighthours.tickgo.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class OrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderSn;

    private Long userId;

    private String username;

    private Long trainId;

    private String trainNumber;

    private String departure;

    private String arrival;

    private Integer status;

    private LocalDateTime orderTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
