package com.eighthours.tickgo.order.mq;

import com.eighthours.tickgo.order.exception.BizException;
import com.eighthours.tickgo.order.dto.OrderCancelDelayMessage;
import com.eighthours.tickgo.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "tickgo-order-cancel",
        consumerGroup = "tickgo-order-cancel-consumer-group"
)
public class OrderCancelConsumer implements RocketMQListener<OrderCancelDelayMessage> {

    @Autowired
    private OrderService orderService;

    @Override
    public void onMessage(OrderCancelDelayMessage message) {
        String orderSn = message.getOrderSn();
        log.info("收到延迟取消订单消息, orderSn: {}", orderSn);

        try {
            orderService.cancelOrder(orderSn);
            log.info("延迟取消订单成功, orderSn: {}", orderSn);
        } catch (BizException e) {
            log.warn("延迟取消订单失败（业务异常）, orderSn: {}, 原因: {}", orderSn, e.getMessage());
        } catch (Exception e) {
            log.error("延迟取消订单失败（系统异常）, orderSn: {}", orderSn, e);
            throw e;
        }
    }
}
