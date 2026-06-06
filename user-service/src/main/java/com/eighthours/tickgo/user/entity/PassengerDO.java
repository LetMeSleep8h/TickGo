package com.eighthours.tickgo.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_passenger")
public class PassengerDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String idCard;

    private String phone;

    private Integer type;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
