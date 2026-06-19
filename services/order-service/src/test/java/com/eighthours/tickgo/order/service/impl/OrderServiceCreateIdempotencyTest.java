package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.eighthours.tickgo.order.dto.CreateOrderRequestDTO;
import com.eighthours.tickgo.order.entity.OrderDO;
import com.eighthours.tickgo.order.entity.OrderItemDO;
import com.eighthours.tickgo.order.enums.OrderStatusEnum;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceCreateIdempotencyTest {

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
    void createOrderShouldIgnoreDuplicateOrderSn() {
        OrderDO existedOrder = new OrderDO();
        existedOrder.setOrderSn("ORD-DUP");
        existedOrder.setStatus(OrderStatusEnum.WAIT_PAY.getCode());
        when(orderMapper.selectOne(any())).thenReturn(existedOrder);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> orderService.createOrder(buildCreateOrderRequest()));

            verify(orderMapper, never()).insert(org.mockito.ArgumentMatchers.<OrderDO>any());
            verify(orderItemMapper, never()).insert(org.mockito.ArgumentMatchers.<OrderItemDO>any());
            verify(orderCancelProducer, never()).sendDefaultCancelDelayMessage(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private CreateOrderRequestDTO buildCreateOrderRequest() {
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setOrderSn("ORD-DUP");
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
}
