package com.eighthours.tickgo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_order_item")
public class OrderItemDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderSn;

    private Long userId;

    private String username;

    private Long trainId;

    private String carriageNumber;

    private String seatNumber;

    private Integer seatType;

    private String realName;

    private String idCard;

    private Integer status;

    private Integer amount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
