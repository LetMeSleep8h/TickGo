package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;
import com.eighthours.tickgo.order.dto.OrderDetailRespDTO;
import com.eighthours.tickgo.order.entity.OrderDO;
import com.eighthours.tickgo.order.entity.OrderItemDO;
import com.eighthours.tickgo.order.enums.OrderStatusEnum;
import com.eighthours.tickgo.order.exception.BizException;
import com.eighthours.tickgo.order.feign.TicketServiceClient;
import com.eighthours.tickgo.order.mapper.OrderItemMapper;
import com.eighthours.tickgo.order.mapper.OrderMapper;
import com.eighthours.tickgo.order.mq.OrderCancelProducer;
import com.eighthours.tickgo.order.service.CompensationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), OrderDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), OrderItemDO.class);
    }

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderCancelProducer orderCancelProducer;

    @Mock
    private TicketServiceClient ticketServiceClient;

    @Mock
    private CompensationService compensationService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void cancelOrderShouldIgnoreDuplicateConsumeWhenOrderAlreadyCanceled() {
        OrderDO waitingOrder = buildOrder(OrderStatusEnum.WAIT_PAY.getCode());
        OrderDO canceledOrder = buildOrder(OrderStatusEnum.CANCELED.getCode());
        when(orderMapper.selectOne(any()))
                .thenReturn(waitingOrder)
                .thenReturn(canceledOrder);
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> orderService.cancelOrder("ORD-1"));
        verify(orderItemMapper).update(any(), any());
    }

    @Test
    void cancelOrderShouldIgnoreDuplicateConsumeWhenPaidOrderAlreadyCanceled() {
        OrderDO paidOrder = buildOrder(OrderStatusEnum.PAID.getCode());
        OrderDO canceledOrder = buildOrder(OrderStatusEnum.CANCELED.getCode());
        when(orderMapper.selectOne(any()))
                .thenReturn(paidOrder)
                .thenReturn(canceledOrder);
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> orderService.cancelOrder("ORD-2"));
        verify(orderItemMapper).update(any(), any());
    }

    @Test
    void cancelOrderShouldThrowWhenOrderStillWaitingAfterFailedUpdate() {
        OrderDO waitingOrder = buildOrder(OrderStatusEnum.WAIT_PAY.getCode());
        when(orderMapper.selectOne(any()))
                .thenReturn(waitingOrder)
                .thenReturn(waitingOrder);
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThrows(BizException.class, () -> orderService.cancelOrder("ORD-3"));
        verify(orderItemMapper).update(any(), any());
    }

    @Test
    void cancelOrderShouldRegisterReleaseAfterSuccessfulCancel() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            OrderDO waitingOrder = buildOrder(OrderStatusEnum.WAIT_PAY.getCode());
            when(orderMapper.selectOne(any())).thenReturn(waitingOrder);
            when(orderMapper.update(any(), any())).thenReturn(1);

            assertDoesNotThrow(() -> orderService.cancelOrder("ORD-4"));
            verify(orderItemMapper).update(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cancelOrderShouldAllowPaidOrderRefundAndReleaseTicket() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            OrderDO paidOrder = buildOrder(OrderStatusEnum.PAID.getCode());
            paidOrder.setOrderSn("ORD-PAID");
            when(orderMapper.selectOne(any())).thenReturn(paidOrder);
            when(orderMapper.update(any(), any())).thenReturn(1);

            assertDoesNotThrow(() -> orderService.cancelOrder("ORD-PAID"));

            verify(orderItemMapper).update(any(), any());
            verify(orderMapper).update(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createOrderShouldCreateCompensationTaskWhenDelayMessageSendFails() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            CreateOrderRequestDTO request = buildCreateOrderRequest();
            doThrow(new RuntimeException("mq down"))
                    .when(orderCancelProducer)
                    .sendDefaultCancelDelayMessage("ORD-5");

            assertDoesNotThrow(() -> orderService.createOrder(request));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(compensationService).createCompensationTask(
                    eq(CompensationServiceImpl.TASK_TYPE_SEND_CANCEL_DELAY_MESSAGE), eq("ORD-5"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void handlePayNotifyShouldReturnWhenOrderAlreadyPaid() {
        OrderDO paidOrder = buildOrder(OrderStatusEnum.PAID.getCode());
        when(orderMapper.selectOne(any())).thenReturn(paidOrder);

        assertDoesNotThrow(() -> orderService.handlePayNotify("PAY-1", "ORD-6"));
        verify(ticketServiceClient, never()).confirmTickets(any());
    }

    @Test
    void getOrderStatusShouldReturnCurrentStatus() {
        OrderDO waitingOrder = buildOrder(OrderStatusEnum.WAIT_PAY.getCode());
        waitingOrder.setOrderSn("ORD-7");
        when(orderMapper.selectOne(any())).thenReturn(waitingOrder);

        assertEquals(OrderStatusEnum.WAIT_PAY.getCode(), orderService.getOrderStatus("ORD-7").getStatus());
    }

    @Test
    void listOrdersShouldReturnAllOrdersForUserWithItems() {
        OrderDO latestOrder = buildOrder(OrderStatusEnum.WAIT_PAY.getCode());
        latestOrder.setOrderSn("ORD-9");
        latestOrder.setUserId(1L);
        latestOrder.setUsername("admin");
        latestOrder.setTrainId(1002L);
        latestOrder.setTrainNumber("G1002");
        latestOrder.setDeparture("杭州东");
        latestOrder.setArrival("南京南");
        latestOrder.setOrderTime(LocalDateTime.of(2026, 6, 19, 15, 30, 0));

        OrderDO olderOrder = buildOrder(OrderStatusEnum.CANCELED.getCode());
        olderOrder.setOrderSn("ORD-8");
        olderOrder.setUserId(1L);
        olderOrder.setUsername("admin");
        olderOrder.setTrainId(1001L);
        olderOrder.setTrainNumber("G1001");
        olderOrder.setDeparture("北京南");
        olderOrder.setArrival("上海虹桥");
        olderOrder.setOrderTime(LocalDateTime.of(2026, 6, 19, 15, 0, 0));

        OrderItemDO latestItem = buildOrderItem("ORD-9", "Passenger-101", "01", "01A", 1, 5600);
        OrderItemDO olderItem = buildOrderItem("ORD-8", "Passenger-102", "02", "02B", 2, 4300);

        when(orderMapper.selectList(any())).thenReturn(List.of(latestOrder, olderOrder));
        when(orderItemMapper.selectList(any()))
                .thenReturn(List.of(latestItem))
                .thenReturn(List.of(olderItem));

        List<OrderDetailRespDTO> orders = orderService.listOrders(1L);

        assertEquals(2, orders.size());
        assertEquals("ORD-9", orders.get(0).getOrderSn());
        assertEquals(OrderStatusEnum.WAIT_PAY.getCode(), orders.get(0).getStatus());
        assertNotNull(orders.get(0).getItems());
        assertEquals(1, orders.get(0).getItems().size());
        assertEquals("Passenger-101", orders.get(0).getItems().get(0).getPassengerName());
        assertEquals(LocalDateTime.of(2026, 6, 19, 15, 30, 30), orders.get(0).getExpireTime());
        assertEquals("ORD-8", orders.get(1).getOrderSn());
        assertEquals(OrderStatusEnum.CANCELED.getCode(), orders.get(1).getStatus());
        assertEquals(LocalDateTime.of(2026, 6, 19, 15, 0, 30), orders.get(1).getExpireTime());
    }

    private OrderDO buildOrder(Integer status) {
        OrderDO order = new OrderDO();
        order.setOrderSn("ORD");
        order.setStatus(status);
        return order;
    }

    private CreateOrderRequestDTO buildCreateOrderRequest() {
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setOrderSn("ORD-5");
        request.setUserId(1L);
        request.setUsername("tester");
        request.setTrainId(1001L);
        request.setTrainNumber("G1001");
        request.setDeparture("Hangzhou");
        request.setArrival("Shanghai");

        CreateOrderRequestDTO.OrderItemDTO item = new CreateOrderRequestDTO.OrderItemDTO();
        item.setPassengerId(10L);
        item.setSeatType(1);
        item.setCarriageNumber("01");
        item.setSeatNumber("A1");
        item.setAmount(100);
        request.setItems(List.of(item));
        return request;
    }

    private OrderItemDO buildOrderItem(String orderSn,
                                       String realName,
                                       String carriageNumber,
                                       String seatNumber,
                                       Integer seatType,
                                       Integer amount) {
        OrderItemDO item = new OrderItemDO();
        item.setOrderSn(orderSn);
        item.setRealName(realName);
        item.setCarriageNumber(carriageNumber);
        item.setSeatNumber(seatNumber);
        item.setSeatType(seatType);
        item.setAmount(amount);
        return item;
    }
}
