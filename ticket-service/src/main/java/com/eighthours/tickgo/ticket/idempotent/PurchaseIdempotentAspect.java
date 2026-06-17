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

    @Around("@annotation(idempotent) && args(request,..)")
    public Object around(ProceedingJoinPoint joinPoint,
                         Idempotent idempotent,
                         NewTicketPurchaseReqDTO request) throws Throwable {
        String key = keyResolver.buildKey(idempotent.prefix(), request);
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
        Boolean locked = valueOperations.setIfAbsent(
                key,
                IdempotentStatus.PROCESSING.name(),
                idempotent.processingTtlSeconds(),
                TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            String status = valueOperations.get(key);
            if (IdempotentStatus.SUCCESS.name().equals(status)) {
                throw new BizException("购票请求已提交成功，请勿重复下单");
            }
            throw new BizException("购票请求处理中，请勿重复提交");
        }
        try {
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
}
