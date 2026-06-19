package com.eighthours.tickgo.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_payment_callback_task")
public class PaymentCallbackTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentSn;

    private String orderSn;

    private Integer status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryTime;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
