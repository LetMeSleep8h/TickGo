package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.order.dto.TicketOrderRequestDTO;
import com.eighthours.tickgo.order.enums.OrderItemStatusEnum;
import com.eighthours.tickgo.order.enums.OrderStatusEnum;
import com.eighthours.tickgo.order.exception.BizException;
import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;
import com.eighthours.tickgo.order.dto.OrderDetailRespDTO;
import com.eighthours.tickgo.order.dto.OrderStatusRespDTO;
import com.eighthours.tickgo.order.entity.OrderDO;
import com.eighthours.tickgo.order.entity.OrderItemDO;
import com.eighthours.tickgo.order.feign.TicketServiceClient;
import com.eighthours.tickgo.order.mapper.OrderItemMapper;
import com.eighthours.tickgo.order.mapper.OrderMapper;
import com.eighthours.tickgo.order.mq.OrderCancelProducer;
import com.eighthours.tickgo.order.service.CompensationService;
import com.eighthours.tickgo.order.service.OrderService;
import com.eighthours.tickgo.order.ticket.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    public void createOrder(CreateOrderRequestDTO request) {
        OrderDO existedOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderSn, request.getOrderSn()));
        if (existedOrder != null) {
            log.info("订单已存在，忽略重复创建，orderSn={}", request.getOrderSn());
            return;
        }

        OrderDO order = new OrderDO();
        order.setOrderSn(request.getOrderSn());
        order.setUserId(request.getUserId());
        order.setUsername(request.getUsername());
        order.setTrainId(request.getTrainId());
        order.setTrainNumber(request.getTrainNumber());
        order.setDeparture(request.getDeparture());
        order.setArrival(request.getArrival());
        order.setStatus(OrderStatusEnum.WAIT_PAY.getCode());
        order.setOrderTime(LocalDateTime.now());
        orderMapper.insert(order);

        for (CreateOrderRequestDTO.OrderItemDTO item : request.getItems()) {
            OrderItemDO orderItem = new OrderItemDO();
            orderItem.setOrderSn(request.getOrderSn());
            orderItem.setUserId(request.getUserId());
            orderItem.setUsername(request.getUsername());
            orderItem.setTrainId(request.getTrainId());
            orderItem.setCarriageNumber(item.getCarriageNumber());
            orderItem.setSeatNumber(item.getSeatNumber());
            orderItem.setSeatType(item.getSeatType());
            orderItem.setRealName("Passenger-" + item.getPassengerId());
            orderItem.setIdCard("MOCK-" + item.getPassengerId());
            orderItem.setStatus(OrderItemStatusEnum.WAIT_PAY.getCode());
            orderItem.setAmount(item.getAmount());
            orderItemMapper.insert(orderItem);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    orderCancelProducer.sendDefaultCancelDelayMessage(request.getOrderSn());
                } catch (Exception e) {
                    log.error("发送延迟取消消息失败，创建补偿任务，orderSn={}", request.getOrderSn(), e);
                    compensationService.createCompensationTask(
                            CompensationServiceImpl.TASK_TYPE_SEND_CANCEL_DELAY_MESSAGE, request.getOrderSn());
                }
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
                    Result<Void> result = ticketServiceClient.confirmTickets(new TicketOrderRequestDTO(orderSn));
                    if (result.getCode() != 200) {
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

        if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
            return;
        }
        if (OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
            log.info("订单已支付，忽略延迟取消消息，orderSn={}", orderSn);
            return;
        }
        if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
            throw new BizException("订单状态异常，不能取消");
        }
        Integer currentOrderStatus = order.getStatus();
        Integer currentOrderItemStatus = OrderItemStatusEnum.WAIT_PAY.getCode();

        orderItemMapper.update(null,
                new LambdaUpdateWrapper<OrderItemDO>()
                        .set(OrderItemDO::getStatus, OrderItemStatusEnum.CANCELED.getCode())
                        .eq(OrderItemDO::getOrderSn, orderSn)
                        .eq(OrderItemDO::getStatus, currentOrderItemStatus));

        int orderUpdated = orderMapper.update(null,
                new LambdaUpdateWrapper<OrderDO>()
                        .set(OrderDO::getStatus, OrderStatusEnum.CANCELED.getCode())
                        .eq(OrderDO::getOrderSn, orderSn)
                        .eq(OrderDO::getStatus, currentOrderStatus));

        if (orderUpdated != 1) {
            OrderDO latestOrder = orderMapper.selectOne(
                    new LambdaQueryWrapper<OrderDO>()
                            .eq(OrderDO::getOrderSn, orderSn));
            if (latestOrder == null) {
                throw new BizException("订单不存在");
            }
            if (OrderStatusEnum.CANCELED.getCode().equals(latestOrder.getStatus())) {
                log.info("订单已被其他请求处理，直接返回，orderSn={}, status={}", orderSn, latestOrder.getStatus());
                return;
            }
            throw new BizException("订单状态更新失败");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Result<Void> result = ticketServiceClient.releaseSeats(new TicketOrderRequestDTO(orderSn));
                    if (result.getCode() != 200) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayNotify(String paymentSn, String orderSn) {
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
            throw new BizException("订单已取消，不能支付成功");
        }

        int updated = orderMapper.update(null,
                new LambdaUpdateWrapper<OrderDO>()
                        .set(OrderDO::getStatus, OrderStatusEnum.PAID.getCode())
                        .eq(OrderDO::getOrderSn, orderSn)
                        .eq(OrderDO::getStatus, OrderStatusEnum.WAIT_PAY.getCode()));
        if (updated != 1) {
            throw new BizException("订单状态更新失败");
        }

        orderItemMapper.update(null,
                new LambdaUpdateWrapper<OrderItemDO>()
                        .set(OrderItemDO::getStatus, OrderItemStatusEnum.PAID.getCode())
                        .eq(OrderItemDO::getOrderSn, orderSn)
                        .eq(OrderItemDO::getStatus, OrderItemStatusEnum.WAIT_PAY.getCode()));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Result<Void> result = ticketServiceClient.confirmTickets(new TicketOrderRequestDTO(orderSn));
                    if (result.getCode() != 200) {
                        log.warn("支付回调后确认车票失败，orderSn={}, paymentSn={}", orderSn, paymentSn);
                        compensationService.createCompensationTask(
                                CompensationServiceImpl.TASK_TYPE_CONFIRM_TICKET, orderSn);
                    }
                } catch (Exception e) {
                    log.error("支付回调后确认车票异常，orderSn={}, paymentSn={}", orderSn, paymentSn, e);
                    compensationService.createCompensationTask(
                            CompensationServiceImpl.TASK_TYPE_CONFIRM_TICKET, orderSn);
                }
            }
        });
    }

    @Override
    public OrderStatusRespDTO getOrderStatus(String orderSn) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        return new OrderStatusRespDTO(orderSn, order.getStatus());
    }

    @Override
    public List<OrderDetailRespDTO> listOrders(Long userId) {
        List<OrderDO> orders = orderMapper.selectList(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getUserId, userId)
                        .orderByDesc(OrderDO::getOrderTime)
                        .orderByDesc(OrderDO::getId));
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        return orders.stream()
                .map(this::buildOrderDetail)
                .toList();
    }

    private OrderDetailRespDTO buildOrderDetail(OrderDO order) {
        OrderDetailRespDTO dto = new OrderDetailRespDTO();
        dto.setOrderSn(order.getOrderSn());
        dto.setUserId(order.getUserId());
        dto.setUsername(order.getUsername());
        dto.setTrainId(order.getTrainId());
        dto.setTrainNumber(order.getTrainNumber());
        dto.setDeparture(order.getDeparture());
        dto.setArrival(order.getArrival());
        dto.setStatus(order.getStatus());
        dto.setOrderTime(order.getOrderTime());
        if (order.getOrderTime() != null) {
            dto.setExpireTime(order.getOrderTime().plusSeconds(OrderCancelProducer.DEFAULT_CANCEL_DELAY_SECONDS));
        }
        List<OrderItemDO> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemDO>()
                        .eq(OrderItemDO::getOrderSn, order.getOrderSn())
                        .orderByAsc(OrderItemDO::getId));
        dto.setItems(orderItems.stream()
                .map(this::buildOrderItemDetail)
                .collect(Collectors.toList()));
        return dto;
    }

    private OrderDetailRespDTO.OrderItemRespDTO buildOrderItemDetail(OrderItemDO orderItem) {
        OrderDetailRespDTO.OrderItemRespDTO dto = new OrderDetailRespDTO.OrderItemRespDTO();
        dto.setPassengerName(orderItem.getRealName());
        dto.setSeatType(orderItem.getSeatType());
        dto.setCarriageNumber(orderItem.getCarriageNumber());
        dto.setSeatNumber(orderItem.getSeatNumber());
        dto.setAmount(orderItem.getAmount());
        return dto;
    }
}
