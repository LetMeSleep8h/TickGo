package com.eighthours.tickgo.order.mq;

import com.eighthours.tickgo.order.dto.OrderCancelDelayMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderCancelProducer {

    private static final String ORDER_CANCEL_TOPIC = "tickgo-order-cancel";
    public static final int DEFAULT_CANCEL_DELAY_LEVEL = 4;
    public static final long DEFAULT_CANCEL_DELAY_SECONDS = 30L;
    private static final long[] DELAY_LEVEL_SECONDS = {
            1, 5, 10, 30, 60, 120, 180, 240, 300,
            360, 420, 480, 540, 600, 1200, 1800, 3600, 7200
    };

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Value("${tickgo.order.mq.enabled:false}")
    private boolean mqEnabled;

    public void sendDefaultCancelDelayMessage(String orderSn) {
        sendCancelDelayMessage(orderSn, DEFAULT_CANCEL_DELAY_LEVEL);
    }

    public void sendCancelDelayMessage(String orderSn, int delayLevel) {
        if (!mqEnabled || rocketMQTemplate == null) {
            log.warn("RocketMQ 未启用，跳过发送延迟取消消息, orderSn={}", orderSn);
            return;
        }
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

    public void sendCancelDelayMessageByRemainingSeconds(String orderSn, long remainingSeconds) {
        if (remainingSeconds <= 0) {
            sendCancelMessage(orderSn);
            return;
        }
        sendCancelDelayMessage(orderSn, resolveDelayLevel(remainingSeconds));
    }

    public void sendCancelMessage(String orderSn) {
        if (!mqEnabled || rocketMQTemplate == null) {
            log.warn("RocketMQ 未启用，跳过发送立即取消消息, orderSn={}", orderSn);
            return;
        }
        OrderCancelDelayMessage message = new OrderCancelDelayMessage();
        message.setOrderSn(orderSn);

        Message<OrderCancelDelayMessage> mqMessage = MessageBuilder.withPayload(message).build();

        try {
            rocketMQTemplate.syncSend(ORDER_CANCEL_TOPIC, mqMessage);
            log.info("发送立即取消订单消息成功, orderSn: {}", orderSn);
        } catch (Exception e) {
            log.error("发送立即取消订单消息失败, orderSn: {}", orderSn, e);
            throw new RuntimeException("发送立即取消订单消息失败", e);
        }
    }

    private int resolveDelayLevel(long remainingSeconds) {
        for (int i = 0; i < DELAY_LEVEL_SECONDS.length; i++) {
            if (remainingSeconds <= DELAY_LEVEL_SECONDS[i]) {
                return i + 1;
            }
        }
        return DELAY_LEVEL_SECONDS.length;
    }
}
