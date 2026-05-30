package com.eighthours.tickgo.mq;

import com.eighthours.tickgo.dto.OrderCancelDelayMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderCancelProducer {

    private static final String ORDER_CANCEL_TOPIC = "tickgo-order-cancel";

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendCancelDelayMessage(String orderSn, int delayLevel) {
        OrderCancelDelayMessage message = new OrderCancelDelayMessage();
        message.setOrderSn(orderSn);

        Message<OrderCancelDelayMessage> mqMessage = MessageBuilder.withPayload(message).build();

        try {
            rocketMQTemplate.syncSend(ORDER_CANCEL_TOPIC, mqMessage, 3000, delayLevel);
            log.info("发送延迟取消订单消息成功, orderSn: {}, delayLevel: {}", orderSn, delayLevel);
        } catch (Exception e) {
            log.error("发送延迟取消订单消息失败, orderSn: {}", orderSn, e);
            throw new RuntimeException("发送延迟取消订单消息失败", e);
        }
    }
}
