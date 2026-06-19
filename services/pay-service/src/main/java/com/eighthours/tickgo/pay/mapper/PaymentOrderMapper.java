package com.eighthours.tickgo.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eighthours.tickgo.pay.entity.PaymentOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderDO> {
}
