package com.eighthours.tickgo.ticket.idempotent;

import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class PurchaseIdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final PurchaseIdempotentKeyResolver keyResolver;
    private final PurchaseBusinessStateChecker purchaseBusinessStateChecker;

    @Around("@annotation(idempotent) && args(request,..)")
    public Object around(ProceedingJoinPoint joinPoint,
                         Idempotent idempotent,
                         NewTicketPurchaseReqDTO request) throws Throwable {
        String key = keyResolver.buildKey(idempotent.prefix(), request);
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        boolean locked = tryAcquireRequestLock(valueOperations, key, idempotent);
        if (!locked) {
            String status = valueOperations.get(key);
            if (IdempotentStatus.PROCESSING.name().equals(status)) {
                throw new BizException("订单构建中，请稍后再试");
            }
            if (IdempotentStatus.SUCCESS.name().equals(status)) {
                try {
                    purchaseBusinessStateChecker.check(request);
                } catch (BizException ex) {
                    throw ex;
                }
                stringRedisTemplate.delete(key);
                locked = tryAcquireRequestLock(valueOperations, key, idempotent);
            }
        }
        if (!locked) {
            throw new BizException("购票请求处理中，请勿重复提交");
        }
        try {
            purchaseBusinessStateChecker.check(request);
            Object result = joinPoint.proceed();
            valueOperations.set(
                    key,
                    IdempotentStatus.SUCCESS.name(),
                    idempotent.successTtlSeconds(),
                    TimeUnit.SECONDS);
            return result;
        } catch (Throwable ex) {
            stringRedisTemplate.delete(key);
            throw ex;
        }
    }

    private boolean tryAcquireRequestLock(ValueOperations<String, String> valueOperations,
                                          String key,
                                          Idempotent idempotent) {
        Boolean locked = valueOperations.setIfAbsent(
                key,
                IdempotentStatus.PROCESSING.name(),
                idempotent.processingTtlSeconds(),
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }
}
