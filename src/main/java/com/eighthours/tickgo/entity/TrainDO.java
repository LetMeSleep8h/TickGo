package com.eighthours.tickgo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_train")
public class TrainDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String trainNumber;

    private String startStation;

    private String endStation;

    private LocalDateTime departureTime;

    private LocalDateTime arrivalTime;

    private Integer saleStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
