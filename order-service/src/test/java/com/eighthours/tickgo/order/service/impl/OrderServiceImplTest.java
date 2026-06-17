package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    void cancelOrderShouldIgnoreDuplicateConsumeWhenOrderAlreadyPaid() {
        OrderDO waitingOrder = buildOrder(OrderStatusEnum.WAIT_PAY.getCode());
        OrderDO paidOrder = buildOrder(OrderStatusEnum.PAID.getCode());
        when(orderMapper.selectOne(any()))
                .thenReturn(waitingOrder)
                .thenReturn(paidOrder);
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

    private OrderDO buildOrder(Integer status) {
        OrderDO order = new OrderDO();
        order.setOrderSn("ORD");
        order.setStatus(status);
        return order;
    }
}
