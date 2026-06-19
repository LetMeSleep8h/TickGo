package com.eighthours.tickgo.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_payment_order")
public class PaymentOrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentSn;

    private String orderSn;

    private Long userId;

    private Integer payAmount;

    private String payChannel;

    private Integer status;

    private Integer callbackStatus;

    private Integer callbackRetryCount;

    private LocalDateTime lastCallbackTime;

    private LocalDateTime successTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
