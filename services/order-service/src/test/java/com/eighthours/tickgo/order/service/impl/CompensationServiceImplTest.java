package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.eighthours.tickgo.order.entity.CompensationTask;
import com.eighthours.tickgo.order.entity.OrderDO;
import com.eighthours.tickgo.order.enums.OrderStatusEnum;
import com.eighthours.tickgo.order.feign.TicketServiceClient;
import com.eighthours.tickgo.order.mapper.CompensationTaskMapper;
import com.eighthours.tickgo.order.mapper.OrderMapper;
import com.eighthours.tickgo.order.mq.OrderCancelProducer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CompensationTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), OrderDO.class);
    }

    @Mock
    private CompensationTaskMapper compensationTaskMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private TicketServiceClient ticketServiceClient;

    @Mock
    private OrderCancelProducer orderCancelProducer;

    @InjectMocks
    private CompensationServiceImpl compensationService;

    @Test
    void retryCompensationTasksShouldResendRemainingDelayMessage() {
        CompensationTask task = buildTask(CompensationServiceImpl.TASK_TYPE_SEND_CANCEL_DELAY_MESSAGE, "ORD-6");
        OrderDO order = buildOrder(OrderStatusEnum.WAIT_PAY.getCode(), LocalDateTime.now().minusSeconds(25));
        when(compensationTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(compensationTaskMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(order);

        compensationService.retryCompensationTasks();

        verify(orderCancelProducer).sendCancelDelayMessageByRemainingSeconds(
                eq("ORD-6"),
                longThat(each -> each >= 1 && each <= 10));
        verify(orderCancelProducer, never()).sendCancelMessage(any());
    }

    @Test
    void retryCompensationTasksShouldSendImmediateCancelWhenExpired() {
        CompensationTask task = buildTask(CompensationServiceImpl.TASK_TYPE_SEND_CANCEL_DELAY_MESSAGE, "ORD-7");
        OrderDO order = buildOrder(OrderStatusEnum.WAIT_PAY.getCode(), LocalDateTime.now().minusSeconds(40));
        when(compensationTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(compensationTaskMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.selectOne(any())).thenReturn(order);

        compensationService.retryCompensationTasks();

        verify(orderCancelProducer).sendCancelMessage("ORD-7");
        verify(orderCancelProducer, never()).sendCancelDelayMessageByRemainingSeconds(any(), anyLong());
    }

    private CompensationTask buildTask(String taskType, String bizId) {
        CompensationTask task = new CompensationTask();
        task.setId(1L);
        task.setTaskType(taskType);
        task.setBizId(bizId);
        task.setStatus(CompensationServiceImpl.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetryCount(5);
        task.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
        return task;
    }

    private OrderDO buildOrder(Integer status, LocalDateTime orderTime) {
        OrderDO order = new OrderDO();
        order.setOrderSn("ORD");
        order.setStatus(status);
        order.setOrderTime(orderTime);
        return order;
    }
}
