package com.eighthours.tickgo.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.pay.common.Result;
import com.eighthours.tickgo.pay.dto.OrderPayNotifyRequestDTO;
import com.eighthours.tickgo.pay.entity.PaymentCallbackTaskDO;
import com.eighthours.tickgo.pay.enums.CallbackStatusEnum;
import com.eighthours.tickgo.pay.feign.OrderServiceClient;
import com.eighthours.tickgo.pay.mapper.PaymentCallbackTaskMapper;
import com.eighthours.tickgo.pay.mapper.PaymentOrderMapper;
import com.eighthours.tickgo.pay.service.PaymentCallbackTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackTaskServiceImpl implements PaymentCallbackTaskService {

    private static final int TASK_PENDING = 0;
    private static final int TASK_PROCESSING = 1;
    private static final int TASK_SUCCESS = 2;
    private static final int TASK_FAILED = 3;

    private final PaymentCallbackTaskMapper paymentCallbackTaskMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderServiceClient orderServiceClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCallbackTask(String paymentSn, String orderSn, String errorMsg) {
        try {
            PaymentCallbackTaskDO task = new PaymentCallbackTaskDO();
            task.setPaymentSn(paymentSn);
            task.setOrderSn(orderSn);
            task.setStatus(TASK_PENDING);
            task.setRetryCount(0);
            task.setMaxRetryCount(5);
            task.setNextRetryTime(LocalDateTime.now());
            task.setErrorMsg(errorMsg);
            paymentCallbackTaskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            log.info("支付回调任务已存在，paymentSn={}", paymentSn);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryTasks() {
        List<PaymentCallbackTaskDO> tasks = paymentCallbackTaskMapper.selectList(
                new LambdaQueryWrapper<PaymentCallbackTaskDO>()
                        .in(PaymentCallbackTaskDO::getStatus, TASK_PENDING, TASK_FAILED)
                        .le(PaymentCallbackTaskDO::getNextRetryTime, LocalDateTime.now())
                        .last("LIMIT 100"));
        for (PaymentCallbackTaskDO task : tasks) {
            paymentCallbackTaskMapper.update(null,
                    new LambdaUpdateWrapper<PaymentCallbackTaskDO>()
                            .set(PaymentCallbackTaskDO::getStatus, TASK_PROCESSING)
                            .eq(PaymentCallbackTaskDO::getId, task.getId())
                            .eq(PaymentCallbackTaskDO::getStatus, task.getStatus()));
            try {
                Result<Void> result = orderServiceClient.notifyPaySuccess(
                        new OrderPayNotifyRequestDTO(task.getPaymentSn(), task.getOrderSn()));
                if (result != null && result.getCode() == 200) {
                    paymentCallbackTaskMapper.update(null,
                            new LambdaUpdateWrapper<PaymentCallbackTaskDO>()
                                    .set(PaymentCallbackTaskDO::getStatus, TASK_SUCCESS)
                                    .set(PaymentCallbackTaskDO::getUpdateTime, LocalDateTime.now())
                                    .eq(PaymentCallbackTaskDO::getId, task.getId()));
                    paymentOrderMapper.update(null,
                            new LambdaUpdateWrapper<com.eighthours.tickgo.pay.entity.PaymentOrderDO>()
                                    .set(com.eighthours.tickgo.pay.entity.PaymentOrderDO::getCallbackStatus, CallbackStatusEnum.SUCCESS.getCode())
                                    .set(com.eighthours.tickgo.pay.entity.PaymentOrderDO::getLastCallbackTime, LocalDateTime.now())
                                    .eq(com.eighthours.tickgo.pay.entity.PaymentOrderDO::getPaymentSn, task.getPaymentSn()));
                    continue;
                }
                handleRetry(task, result == null ? "result is null" : result.getMessage());
            } catch (Exception ex) {
                handleRetry(task, ex.getMessage());
            }
        }
    }

    private void handleRetry(PaymentCallbackTaskDO task, String errorMsg) {
        int retryCount = task.getRetryCount() + 1;
        int nextStatus = retryCount >= task.getMaxRetryCount() ? TASK_FAILED : TASK_PENDING;
        paymentCallbackTaskMapper.update(null,
                new LambdaUpdateWrapper<PaymentCallbackTaskDO>()
                        .set(PaymentCallbackTaskDO::getStatus, nextStatus)
                        .set(PaymentCallbackTaskDO::getRetryCount, retryCount)
                        .set(PaymentCallbackTaskDO::getErrorMsg, errorMsg)
                        .set(PaymentCallbackTaskDO::getNextRetryTime, LocalDateTime.now().plusMinutes(Math.min(30, 1L << Math.min(retryCount, 4))))
                        .set(PaymentCallbackTaskDO::getUpdateTime, LocalDateTime.now())
                        .eq(PaymentCallbackTaskDO::getId, task.getId()));
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<com.eighthours.tickgo.pay.entity.PaymentOrderDO>()
                        .set(com.eighthours.tickgo.pay.entity.PaymentOrderDO::getCallbackStatus, CallbackStatusEnum.FAILED.getCode())
                        .setSql("callback_retry_count = callback_retry_count + 1")
                        .set(com.eighthours.tickgo.pay.entity.PaymentOrderDO::getLastCallbackTime, LocalDateTime.now())
                        .eq(com.eighthours.tickgo.pay.entity.PaymentOrderDO::getPaymentSn, task.getPaymentSn()));
    }
}
