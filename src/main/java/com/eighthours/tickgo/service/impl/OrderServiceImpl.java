package com.eighthours.tickgo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.entity.OrderDO;
import com.eighthours.tickgo.entity.OrderItemDO;
import com.eighthours.tickgo.entity.SeatDO;
import com.eighthours.tickgo.entity.TicketDO;
import com.eighthours.tickgo.entity.TrainStationDO;
import com.eighthours.tickgo.mapper.OrderItemMapper;
import com.eighthours.tickgo.mapper.OrderMapper;
import com.eighthours.tickgo.mapper.SeatMapper;
import com.eighthours.tickgo.enums.OrderItemStatusEnum;
import com.eighthours.tickgo.enums.OrderStatusEnum;
import com.eighthours.tickgo.enums.TicketStatusEnum;
import com.eighthours.tickgo.exception.BizException;
import com.eighthours.tickgo.mapper.TicketMapper;
import com.eighthours.tickgo.mapper.TrainStationMapper;
import com.eighthours.tickgo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final TrainStationMapper trainStationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    private String buildTokenKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:token:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
    }

    private String buildPurchaseLockKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:purchase:lock:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
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
        if (CollectionUtils.isEmpty(orderItems)) {
            throw new BizException("订单明细不存在");
        }

        Long trainId = order.getTrainId();
        String departure = order.getDeparture();
        String arrival = order.getArrival();

        TrainStationDO startStation = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .eq(TrainStationDO::getStationName, departure));
        TrainStationDO endStation = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .eq(TrainStationDO::getStationName, arrival));
        if (startStation == null || endStation == null) {
            throw new BizException("车站信息不存在");
        }

        Integer departureSequence = startStation.getSequenceNo();
        Integer arrivalSequence = endStation.getSequenceNo();

        List<Integer> seatTypes = orderItems.stream()
                .map(OrderItemDO::getSeatType)
                .distinct()
                .collect(Collectors.toList());

        for (Integer seatType : seatTypes) {
            RLock lock = redissonClient.getLock(buildPurchaseLockKey(trainId, departure, arrival, seatType));
            boolean locked = false;
            try {
                locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    throw new BizException("取消订单人数过多，请稍后重试");
                }

                List<OrderItemDO> sameSeatTypeItems = orderItems.stream()
                        .filter(item -> item.getSeatType().equals(seatType))
                        .collect(Collectors.toList());

                int expectedSegmentCount = arrivalSequence - departureSequence;
                for (OrderItemDO orderItem : sameSeatTypeItems) {
                    int updated = seatMapper.update(null,
                            new LambdaUpdateWrapper<SeatDO>()
                                    .set(SeatDO::getSeatStatus, 0)
                                    .eq(SeatDO::getTrainId, trainId)
                                    .eq(SeatDO::getCarriageNumber, orderItem.getCarriageNumber())
                                    .eq(SeatDO::getSeatNumber, orderItem.getSeatNumber())
                                    .eq(SeatDO::getSeatType, seatType)
                                    .ge(SeatDO::getStartSequence, departureSequence)
                                    .le(SeatDO::getEndSequence, arrivalSequence)
                                    .eq(SeatDO::getSeatStatus, 1));
                    
                    if (updated != expectedSegmentCount) {
                        throw new BizException("座位释放失败");
                    }
                    
                    int ticketUpdated = ticketMapper.update(null,
                            new LambdaUpdateWrapper<TicketDO>()
                                    .set(TicketDO::getTicketStatus, TicketStatusEnum.CANCELED.getCode())
                                    .eq(TicketDO::getTrainId, trainId)
                                    .eq(TicketDO::getCarriageNumber, orderItem.getCarriageNumber())
                                    .eq(TicketDO::getSeatNumber, orderItem.getSeatNumber())
                                    .eq(TicketDO::getSeatType, seatType)
                                    .eq(TicketDO::getTicketStatus, TicketStatusEnum.WAIT_PAY.getCode()));
                    
                    if (ticketUpdated != 1) {
                        throw new BizException("车票状态更新失败");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("取消订单过程被中断，请稍后重试");
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        int orderUpdated = orderMapper.update(null,
                new LambdaUpdateWrapper<OrderDO>()
                        .set(OrderDO::getStatus, OrderStatusEnum.CANCELED.getCode())
                        .eq(OrderDO::getOrderSn, orderSn)
                        .eq(OrderDO::getStatus, OrderStatusEnum.WAIT_PAY.getCode()));
        
        if (orderUpdated != 1) {
            throw new BizException("订单状态更新失败");
        }

        int orderItemUpdated = orderItemMapper.update(null,
                new LambdaUpdateWrapper<OrderItemDO>()
                        .set(OrderItemDO::getStatus, OrderItemStatusEnum.CANCELED.getCode())
                        .eq(OrderItemDO::getOrderSn, orderSn)
                        .eq(OrderItemDO::getStatus, OrderItemStatusEnum.WAIT_PAY.getCode()));
        
        if (orderItemUpdated != orderItems.size()) {
            throw new BizException("订单明细状态更新失败");
        }
        
        for (Integer seatType : seatTypes) {
            List<OrderItemDO> sameSeatTypeItems = orderItems.stream()
                    .filter(item -> item.getSeatType().equals(seatType))
                    .collect(Collectors.toList());
            long countToAdd = sameSeatTypeItems.size();
            stringRedisTemplate.opsForValue().increment(
                    buildTokenKey(trainId, departure, arrival, seatType), countToAdd);
        }
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

        Long trainId = order.getTrainId();

        for (OrderItemDO orderItem : orderItems) {
            int ticketUpdated = ticketMapper.update(null,
                    new LambdaUpdateWrapper<TicketDO>()
                            .set(TicketDO::getTicketStatus, TicketStatusEnum.PAID.getCode())
                            .eq(TicketDO::getTrainId, trainId)
                            .eq(TicketDO::getCarriageNumber, orderItem.getCarriageNumber())
                            .eq(TicketDO::getSeatNumber, orderItem.getSeatNumber())
                            .eq(TicketDO::getSeatType, orderItem.getSeatType())
                            .eq(TicketDO::getTicketStatus, TicketStatusEnum.WAIT_PAY.getCode()));

            if (ticketUpdated != 1) {
                throw new BizException("车票状态更新失败");
            }
        }

        int orderItemUpdated = orderItemMapper.update(null,
                new LambdaUpdateWrapper<OrderItemDO>()
                        .set(OrderItemDO::getStatus, OrderItemStatusEnum.PAID.getCode())
                        .eq(OrderItemDO::getOrderSn, orderSn)
                        .eq(OrderItemDO::getStatus, OrderItemStatusEnum.WAIT_PAY.getCode()));

        if (orderItemUpdated != orderItems.size()) {
            throw new BizException("订单明细状态更新失败");
        }

        int orderUpdated = orderMapper.update(null,
                new LambdaUpdateWrapper<OrderDO>()
                        .set(OrderDO::getStatus, OrderStatusEnum.PAID.getCode())
                        .eq(OrderDO::getOrderSn, orderSn)
                        .eq(OrderDO::getStatus, OrderStatusEnum.WAIT_PAY.getCode()));

        if (orderUpdated != 1) {
            throw new BizException("订单状态更新失败");
        }
    }
}
