package com.eighthours.tickgo.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.pay.common.Result;
import com.eighthours.tickgo.pay.dto.CreatePaymentRequestDTO;
import com.eighthours.tickgo.pay.dto.OrderPayNotifyRequestDTO;
import com.eighthours.tickgo.pay.dto.PaymentQueryRespDTO;
import com.eighthours.tickgo.pay.entity.PaymentOrderDO;
import com.eighthours.tickgo.pay.enums.CallbackStatusEnum;
import com.eighthours.tickgo.pay.enums.PaymentStatusEnum;
import com.eighthours.tickgo.pay.exception.BizException;
import com.eighthours.tickgo.pay.feign.OrderServiceClient;
import com.eighthours.tickgo.pay.mapper.PaymentOrderMapper;
import com.eighthours.tickgo.pay.service.PayService;
import com.eighthours.tickgo.pay.service.PaymentCallbackTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderServiceClient orderServiceClient;
    private final PaymentCallbackTaskService paymentCallbackTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentQueryRespDTO createPayment(CreatePaymentRequestDTO request) {
        PaymentOrderDO existed = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrderDO>()
                        .eq(PaymentOrderDO::getOrderSn, request.getOrderSn())
                        .ne(PaymentOrderDO::getStatus, PaymentStatusEnum.CLOSED.getCode()));
        if (existed != null) {
            return toResp(existed);
        }

        PaymentOrderDO paymentOrder = new PaymentOrderDO();
        paymentOrder.setPaymentSn("PAY_" + UUID.randomUUID().toString().replace("-", ""));
        paymentOrder.setOrderSn(request.getOrderSn());
        paymentOrder.setUserId(request.getUserId());
        paymentOrder.setPayAmount(request.getPayAmount());
        paymentOrder.setPayChannel("MOCK");
        paymentOrder.setStatus(PaymentStatusEnum.INIT.getCode());
        paymentOrder.setCallbackStatus(CallbackStatusEnum.PENDING.getCode());
        paymentOrder.setCallbackRetryCount(0);
        paymentOrderMapper.insert(paymentOrder);
        return toResp(paymentOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentQueryRespDTO submitPayment(String paymentSn) {
        PaymentOrderDO paymentOrder = loadByPaymentSn(paymentSn);
        if (PaymentStatusEnum.SUCCESS.getCode().equals(paymentOrder.getStatus())) {
            return toResp(paymentOrder);
        }
        if (PaymentStatusEnum.CLOSED.getCode().equals(paymentOrder.getStatus())) {
            throw new BizException("支付单已关闭");
        }

        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrderDO>()
                        .set(PaymentOrderDO::getStatus, PaymentStatusEnum.SUCCESS.getCode())
                        .set(PaymentOrderDO::getSuccessTime, LocalDateTime.now())
                        .eq(PaymentOrderDO::getId, paymentOrder.getId()));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyOrder(paymentSn, paymentOrder.getOrderSn());
            }
        });

        return queryPayment(paymentSn, null);
    }

    @Override
    public PaymentQueryRespDTO queryPayment(String paymentSn, String orderSn) {
        if ((paymentSn == null || paymentSn.isBlank()) && (orderSn == null || orderSn.isBlank())) {
            throw new BizException("paymentSn 或 orderSn 不能为空");
        }
        PaymentOrderDO paymentOrder = (paymentSn != null && !paymentSn.isBlank())
                ? loadByPaymentSn(paymentSn)
                : loadByOrderSn(orderSn);
        return toResp(paymentOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePayment(String paymentSn) {
        PaymentOrderDO paymentOrder = loadByPaymentSn(paymentSn);
        if (PaymentStatusEnum.SUCCESS.getCode().equals(paymentOrder.getStatus())) {
            throw new BizException("支付单已成功，不能关闭");
        }
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrderDO>()
                        .set(PaymentOrderDO::getStatus, PaymentStatusEnum.CLOSED.getCode())
                        .eq(PaymentOrderDO::getId, paymentOrder.getId()));
    }

    void notifyOrder(String paymentSn, String orderSn) {
        try {
            Result<Void> result = orderServiceClient.notifyPaySuccess(new OrderPayNotifyRequestDTO(paymentSn, orderSn));
            if (result == null || result.getCode() != 200) {
                paymentCallbackTaskService.createCallbackTask(paymentSn, orderSn, result == null ? "result is null" : result.getMessage());
                markCallbackFailed(paymentSn);
                return;
            }
            markCallbackSuccess(paymentSn);
        } catch (Exception ex) {
            paymentCallbackTaskService.createCallbackTask(paymentSn, orderSn, ex.getMessage());
            markCallbackFailed(paymentSn);
        }
    }

    void markCallbackSuccess(String paymentSn) {
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrderDO>()
                        .set(PaymentOrderDO::getCallbackStatus, CallbackStatusEnum.SUCCESS.getCode())
                        .set(PaymentOrderDO::getLastCallbackTime, LocalDateTime.now())
                        .eq(PaymentOrderDO::getPaymentSn, paymentSn));
    }

    void markCallbackFailed(String paymentSn) {
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrderDO>()
                        .set(PaymentOrderDO::getCallbackStatus, CallbackStatusEnum.FAILED.getCode())
                        .setSql("callback_retry_count = callback_retry_count + 1")
                        .set(PaymentOrderDO::getLastCallbackTime, LocalDateTime.now())
                        .eq(PaymentOrderDO::getPaymentSn, paymentSn));
    }

    private PaymentOrderDO loadByPaymentSn(String paymentSn) {
        PaymentOrderDO paymentOrder = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrderDO>().eq(PaymentOrderDO::getPaymentSn, paymentSn));
        if (paymentOrder == null) {
            throw new BizException("支付单不存在");
        }
        return paymentOrder;
    }

    private PaymentOrderDO loadByOrderSn(String orderSn) {
        PaymentOrderDO paymentOrder = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrderDO>().eq(PaymentOrderDO::getOrderSn, orderSn));
        if (paymentOrder == null) {
            throw new BizException("支付单不存在");
        }
        return paymentOrder;
    }

    private PaymentQueryRespDTO toResp(PaymentOrderDO paymentOrder) {
        PaymentQueryRespDTO resp = new PaymentQueryRespDTO();
        resp.setPaymentSn(paymentOrder.getPaymentSn());
        resp.setOrderSn(paymentOrder.getOrderSn());
        resp.setStatus(paymentOrder.getStatus());
        resp.setCallbackStatus(paymentOrder.getCallbackStatus());
        resp.setPayAmount(paymentOrder.getPayAmount());
        resp.setSuccessTime(paymentOrder.getSuccessTime());
        return resp;
    }
}
