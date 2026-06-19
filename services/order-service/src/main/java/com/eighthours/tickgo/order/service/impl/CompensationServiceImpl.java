package com.eighthours.tickgo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.order.dto.TicketOrderRequestDTO;
import com.eighthours.tickgo.order.entity.CompensationTask;
import com.eighthours.tickgo.order.entity.OrderDO;
import com.eighthours.tickgo.order.enums.OrderStatusEnum;
import com.eighthours.tickgo.order.feign.TicketServiceClient;
import com.eighthours.tickgo.order.mapper.OrderMapper;
import com.eighthours.tickgo.order.mapper.CompensationTaskMapper;
import com.eighthours.tickgo.order.mq.OrderCancelProducer;
import com.eighthours.tickgo.order.service.CompensationService;
import com.eighthours.tickgo.order.ticket.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationServiceImpl implements CompensationService {

    private final CompensationTaskMapper compensationTaskMapper;
    private final OrderMapper orderMapper;
    private final TicketServiceClient ticketServiceClient;
    private final OrderCancelProducer orderCancelProducer;

    public static final String TASK_TYPE_CANCEL_TICKET = "CANCEL_TICKET";
    public static final String TASK_TYPE_CONFIRM_TICKET = "CONFIRM_TICKET";
    public static final String TASK_TYPE_SEND_CANCEL_DELAY_MESSAGE = "SEND_CANCEL_DELAY_MESSAGE";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCompensationTask(String taskType, String bizId) {
        try {
            CompensationTask task = new CompensationTask();
            task.setTaskType(taskType);
            task.setBizId(bizId);
            task.setStatus(STATUS_PENDING);
            task.setRetryCount(0);
            task.setMaxRetryCount(5);
            task.setNextRetryTime(LocalDateTime.now());
            compensationTaskMapper.insert(task);
            log.info("创建补偿任务成功，taskType={}, bizId={}", taskType, bizId);
        } catch (DuplicateKeyException e) {
            log.info("补偿任务已存在，忽略重复创建，taskType={}, bizId={}", taskType, bizId);
        } catch (Exception e) {
            log.error("创建补偿任务失败，taskType={}, bizId={}", taskType, bizId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryCompensationTasks() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CompensationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CompensationTask::getStatus, STATUS_PENDING, STATUS_FAILED)
                .le(CompensationTask::getNextRetryTime, now)
                .last("LIMIT 100");
        List<CompensationTask> tasks = compensationTaskMapper.selectList(wrapper);

        for (CompensationTask task :
                tasks) {
            try {
                compensationTaskMapper.update(null,
                        new LambdaUpdateWrapper<CompensationTask>()
                                .set(CompensationTask::getStatus, STATUS_PROCESSING)
                                .set(CompensationTask::getUpdateTime, LocalDateTime.now())
                                .eq(CompensationTask::getId, task.getId())
                                .eq(CompensationTask::getStatus, task.getStatus())
                );

                boolean success = executeCompensation(task);

                if (success) {
                    compensationTaskMapper.update(null,
                            new LambdaUpdateWrapper<CompensationTask>()
                                    .set(CompensationTask::getStatus, STATUS_SUCCESS)
                                    .set(CompensationTask::getUpdateTime, LocalDateTime.now())
                                    .eq(CompensationTask::getId, task.getId())
                    );
                    log.info("补偿任务执行成功，id={}, taskType={}, bizId={}", task.getId(), task.getTaskType(), task.getBizId());
                } else {
                    handleRetry(task);
                }
            } catch (Exception e) {
                log.error("补偿任务执行异常，id={}, taskType={}, bizId={}", task.getId(), task.getTaskType(), task.getBizId(), e);
                try {
                    handleRetry(task);
                } catch (Exception ex) {
                    log.error("处理重试逻辑失败，id={}", task.getId(), ex);
                }
            }
        }
    }

    private boolean executeCompensation(CompensationTask task) {
        try {
            switch (task.getTaskType()) {
                case TASK_TYPE_CANCEL_TICKET:
                    Result<Void> cancelResult = ticketServiceClient.releaseSeats(new TicketOrderRequestDTO(task.getBizId()));
                    return cancelResult.getCode() == 200;
                case TASK_TYPE_CONFIRM_TICKET:
                    Result<Void> confirmResult = ticketServiceClient.confirmTickets(new TicketOrderRequestDTO(task.getBizId()));
                    return confirmResult.getCode() == 200;
                case TASK_TYPE_SEND_CANCEL_DELAY_MESSAGE:
                    return resendCancelDelayMessage(task.getBizId());
                default:
                    log.warn("未知任务类型，taskType={}", task.getTaskType());
                    return true;
            }
        } catch (Exception e) {
            log.error("执行补偿失败，id={}, taskType={}, bizId={}", task.getId(), task.getTaskType(), task.getBizId(), e);
            return false;
        }
    }

    private boolean resendCancelDelayMessage(String orderSn) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderSn, orderSn));
        if (order == null) {
            log.info("订单不存在，忽略延迟消息补偿，orderSn={}", orderSn);
            return true;
        }
        if (!OrderStatusEnum.WAIT_PAY.getCode().equals(order.getStatus())) {
            log.info("订单已非待支付状态，忽略延迟消息补偿，orderSn={}, status={}", orderSn, order.getStatus());
            return true;
        }

        LocalDateTime orderTime = order.getOrderTime() != null ? order.getOrderTime() : order.getCreateTime();
        if (orderTime == null) {
            orderCancelProducer.sendCancelMessage(orderSn);
            return true;
        }

        LocalDateTime expireTime = orderTime.plusSeconds(OrderCancelProducer.DEFAULT_CANCEL_DELAY_SECONDS);
        long remainingSeconds = Duration.between(LocalDateTime.now(), expireTime).getSeconds();
        if (remainingSeconds > 0) {
            orderCancelProducer.sendCancelDelayMessageByRemainingSeconds(orderSn, remainingSeconds);
        } else {
            orderCancelProducer.sendCancelMessage(orderSn);
        }
        return true;
    }

    private void handleRetry(CompensationTask task) {
        int newRetryCount = task.getRetryCount() + 1;
        int newStatus = newRetryCount >= task.getMaxRetryCount() ? STATUS_FAILED : STATUS_PENDING;
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(Math.min(30, (long) Math.pow(2, newRetryCount)));

        compensationTaskMapper.update(null,
                new LambdaUpdateWrapper<CompensationTask>()
                        .set(CompensationTask::getStatus, newRetryCount >= task.getMaxRetryCount() ? STATUS_FAILED : STATUS_PENDING)
                        .set(CompensationTask::getRetryCount, newRetryCount)
                        .set(CompensationTask::getNextRetryTime, nextRetryTime)
                        .set(CompensationTask::getUpdateTime, LocalDateTime.now())
                        .eq(CompensationTask::getId, task.getId())
        );
        log.warn("补偿任务需要重试，id={}, taskType={}, bizId={}, retryCount={}", task.getId(), task.getTaskType(), task.getBizId(), newRetryCount);
    }
}
