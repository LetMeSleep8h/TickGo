package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.common.Result;
import com.eighthours.tickgo.enums.OrderItemStatusEnum;
import com.eighthours.tickgo.enums.OrderStatusEnum;
import com.eighthours.tickgo.exception.BizException;
import com.eighthours.tickgo.order.entity.OrderDO;
import com.eighthours.tickgo.order.entity.OrderItemDO;
import com.eighthours.tickgo.order.feign.TicketServiceClient;
import com.eighthours.tickgo.order.mapper.OrderItemMapper;
import com.eighthours.tickgo.order.mapper.OrderMapper;
import com.eighthours.tickgo.order.mq.OrderCancelProducer;
import com.eighthours.tickgo.order.service.CompensationService;
import com.eighthours.tickgo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    public static final String TASK_TYPE_CONFIRM_TICKET = "CONFIRM_TICKET";
    public static final String TASK_TYPE_CANCEL_TICKET = "CANCEL_TICKET";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderCancelProducer orderCancelProducer;
    private final TicketServiceClient ticketServiceClient;
    private final CompensationService compensationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(String orderSn, Long userId, String username, Long trainId, String trainNumber,
                            String departure, String arrival, List<Map<String, Object>> items) {
        OrderDO order = new OrderDO();
        order.setOrderSn(orderSn);
        order.setUserId(userId);
        order.setUsername(username);
        order.setTrainId(trainId);
        order.setTrainNumber(trainNumber);
        order.setDeparture(departure);
        order.setArrival(arrival);
        order.setStatus(OrderStatusEnum.WAIT_PAY.getCode());
        order.setOrderTime(LocalDateTime.now());
        orderMapper.insert(order);

        for (Map<String, Object> item : items) {
            OrderItemDO orderItem = new OrderItemDO();
            orderItem.setOrderSn(orderSn);
            orderItem.setUserId(userId);
            orderItem.setUsername(username);
            orderItem.setTrainId(trainId);
            orderItem.setCarriageNumber((String) item.get("carriageNumber"));
            orderItem.setSeatNumber((String) item.get("seatNumber"));
            orderItem.setSeatType((Integer) item.get("seatType"));
            orderItem.setRealName("Passenger-" + item.get("passengerId"));
            orderItem.setIdCard("MOCK-" + item.get("passengerId"));
            orderItem.setStatus(OrderItemStatusEnum.WAIT_PAY.getCode());
            orderItem.setAmount((Integer) item.get("amount"));
            orderItemMapper.insert(orderItem);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderCancelProducer.sendCancelDelayMessage(orderSn, 4);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderSn) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }

        if (OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
            return;
        }
        if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            throw new BizException("订单已取消，不能支付");
        }
        if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
            throw new BizException("订单状态异常，不能支付");
        }

        List<OrderItemDO> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemDO>()
                        .eq(OrderItemDO::getOrderSn, orderSn));
        if (CollectionUtils.isEmpty(orderItems)) {
            throw new BizException("订单明细不存在");
        }

        for (OrderItemDO orderItem : orderItems) {
            orderItemMapper.update(null,
                    new LambdaUpdateWrapper<OrderItemDO>()
                            .set(OrderItemDO::getStatus, OrderItemStatusEnum.PAID.getCode())
                            .eq(OrderItemDO::getOrderSn, orderSn)
                            .eq(OrderItemDO::getStatus, OrderItemStatusEnum.WAIT_PAY.getCode()));
        }

        orderMapper.update(null,
                new LambdaUpdateWrapper<OrderDO>()
                        .set(OrderDO::getStatus, OrderStatusEnum.PAID.getCode())
                        .eq(OrderDO::getOrderSn, orderSn)
                        .eq(OrderDO::getStatus, OrderStatusEnum.WAIT_PAY.getCode()));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Result<Void> result = ticketServiceClient.confirmTickets(orderSn);
                    if (!"0".equals(result.getCode())) {
                        log.warn("支付后确认车票失败，orderSn={}, 开始补偿", orderSn);
                        compensationService.createCompensationTask(
                                CompensationServiceImpl.TASK_TYPE_CONFIRM_TICKET, orderSn);
                    }
                } catch (Exception e) {
                    log.error("支付后确认车票异常，orderSn={}, 开始补偿", orderSn, e);
                    compensationService.createCompensationTask(
                            CompensationServiceImpl.TASK_TYPE_CONFIRM_TICKET, orderSn);
                }
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderSn) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }

        if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
            return;
        }

        List<OrderItemDO> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemDO>()
                        .eq(OrderItemDO::getOrderSn, orderSn));

        int orderItemUpdated = orderItemMapper.update(null,
                new LambdaUpdateWrapper<OrderItemDO>()
                        .set(OrderItemDO::getStatus, OrderItemStatusEnum.CANCELED.getCode())
                        .eq(OrderItemDO::getOrderSn, orderSn)
                        .eq(OrderItemDO::getStatus, OrderItemStatusEnum.WAIT_PAY.getCode()));

        int orderUpdated = orderMapper.update(null,
                new LambdaUpdateWrapper<OrderDO>()
                        .set(OrderDO::getStatus, OrderStatusEnum.CANCELED.getCode())
                        .eq(OrderDO::getOrderSn, orderSn)
                        .eq(OrderDO::getStatus, OrderStatusEnum.WAIT_PAY.getCode()));

        if (orderUpdated != 1) {
            throw new BizException("订单状态更新失败");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Result<Void> result = ticketServiceClient.releaseSeats(orderSn);
                    if (!"0".equals(result.getCode())) {
                        log.warn("取消后释放座位失败，orderSn={}, 开始补偿", orderSn);
                        compensationService.createCompensationTask(
                                CompensationServiceImpl.TASK_TYPE_CANCEL_TICKET, orderSn);
                    }
                } catch (Exception e) {
                    log.error("取消后释放座位异常，orderSn={}, 开始补偿", orderSn, e);
                    compensationService.createCompensationTask(
                            CompensationServiceImpl.TASK_TYPE_CANCEL_TICKET, orderSn);
                }
            }
        });
    }
}
