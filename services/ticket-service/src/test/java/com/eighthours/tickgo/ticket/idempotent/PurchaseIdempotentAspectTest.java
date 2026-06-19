package com.eighthours.tickgo.ticket.idempotent;

import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseIdempotentAspectTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private PurchaseBusinessStateChecker purchaseBusinessStateChecker;

    @Test
    void around_shouldMarkSuccessWhenRequestFirstArrives() throws Throwable {
        PurchaseIdempotentAspect aspect = new PurchaseIdempotentAspect(
                stringRedisTemplate, new PurchaseIdempotentKeyResolver(), purchaseBusinessStateChecker);
        Idempotent annotation = findAnnotation();
        NewTicketPurchaseReqDTO request = buildRequest();
        String key = new PurchaseIdempotentKeyResolver().buildKey(annotation.prefix(), request);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq(IdempotentStatus.PROCESSING.name()), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.TRUE);
        Object expected = new Object();
        when(joinPoint.proceed()).thenReturn(expected);

        Object actual = aspect.around(joinPoint, annotation, request);

        assertSame(expected, actual);
        verify(valueOperations).set(key, IdempotentStatus.SUCCESS.name(), annotation.successTtlSeconds(), TimeUnit.SECONDS);
        verify(stringRedisTemplate, never()).delete(key);
    }

    @Test
    void around_shouldRejectProcessingDuplicateRequest() throws Throwable {
        PurchaseIdempotentAspect aspect = new PurchaseIdempotentAspect(
                stringRedisTemplate, new PurchaseIdempotentKeyResolver(), purchaseBusinessStateChecker);
        Idempotent annotation = findAnnotation();
        NewTicketPurchaseReqDTO request = buildRequest();
        String key = new PurchaseIdempotentKeyResolver().buildKey(annotation.prefix(), request);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq(IdempotentStatus.PROCESSING.name()), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.FALSE);
        when(valueOperations.get(key)).thenReturn(IdempotentStatus.PROCESSING.name());

        BizException exception = assertThrows(BizException.class, () -> aspect.around(joinPoint, annotation, request));

        assertEquals("订单构建中，请稍后再试", exception.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void around_shouldRejectSuccessfulDuplicateRequestByTicketStatus() throws Throwable {
        PurchaseIdempotentAspect aspect = new PurchaseIdempotentAspect(
                stringRedisTemplate, new PurchaseIdempotentKeyResolver(), purchaseBusinessStateChecker);
        Idempotent annotation = findAnnotation();
        NewTicketPurchaseReqDTO request = buildRequest();
        String key = new PurchaseIdempotentKeyResolver().buildKey(annotation.prefix(), request);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq(IdempotentStatus.PROCESSING.name()), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.FALSE);
        when(valueOperations.get(key)).thenReturn(IdempotentStatus.SUCCESS.name());
        doThrow(new BizException("乘车人存在待支付订单，passengerId=1001"))
                .when(purchaseBusinessStateChecker)
                .check(request);

        BizException exception = assertThrows(BizException.class, () -> aspect.around(joinPoint, annotation, request));

        assertEquals("乘车人存在待支付订单，passengerId=1001", exception.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void around_shouldRetryWhenSuccessfulFingerprintIsStale() throws Throwable {
        PurchaseIdempotentAspect aspect = new PurchaseIdempotentAspect(
                stringRedisTemplate, new PurchaseIdempotentKeyResolver(), purchaseBusinessStateChecker);
        Idempotent annotation = findAnnotation();
        NewTicketPurchaseReqDTO request = buildRequest();
        String key = new PurchaseIdempotentKeyResolver().buildKey(annotation.prefix(), request);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq(IdempotentStatus.PROCESSING.name()), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.FALSE)
                .thenReturn(Boolean.TRUE);
        when(valueOperations.get(key)).thenReturn(IdempotentStatus.SUCCESS.name());
        Object expected = new Object();
        when(joinPoint.proceed()).thenReturn(expected);

        Object actual = aspect.around(joinPoint, annotation, request);

        assertSame(expected, actual);
        verify(stringRedisTemplate).delete(key);
        verify(valueOperations).set(key, IdempotentStatus.SUCCESS.name(), annotation.successTtlSeconds(), TimeUnit.SECONDS);
    }

    @Test
    void around_shouldDeleteKeyWhenBusinessFails() throws Throwable {
        PurchaseIdempotentAspect aspect = new PurchaseIdempotentAspect(
                stringRedisTemplate, new PurchaseIdempotentKeyResolver(), purchaseBusinessStateChecker);
        Idempotent annotation = findAnnotation();
        NewTicketPurchaseReqDTO request = buildRequest();
        String key = new PurchaseIdempotentKeyResolver().buildKey(annotation.prefix(), request);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq(IdempotentStatus.PROCESSING.name()), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(Boolean.TRUE);
        RuntimeException expected = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(expected);

        RuntimeException actual = assertThrows(RuntimeException.class, () -> aspect.around(joinPoint, annotation, request));

        assertSame(expected, actual);
        verify(stringRedisTemplate).delete(key);
    }

    private Idempotent findAnnotation() throws NoSuchMethodException {
        Method method = IdempotentTarget.class.getDeclaredMethod("purchase", NewTicketPurchaseReqDTO.class);
        return method.getAnnotation(Idempotent.class);
    }

    private NewTicketPurchaseReqDTO buildRequest() {
        NewTicketPurchaseReqDTO request = new NewTicketPurchaseReqDTO();
        request.setUserId(1L);
        request.setTrainId(10L);
        request.setDeparture("北京南");
        request.setArrival("杭州东");
        request.setPassengers(List.of(buildPassenger(1001L, 1)));
        return request;
    }

    private NewTicketPurchaseReqDTO.PassengerDTO buildPassenger(Long passengerId, Integer seatType) {
        NewTicketPurchaseReqDTO.PassengerDTO passenger = new NewTicketPurchaseReqDTO.PassengerDTO();
        passenger.setPassengerId(passengerId);
        passenger.setSeatType(seatType);
        return passenger;
    }

    private static final class IdempotentTarget {

        @Idempotent(prefix = "ticket:purchase")
        public void purchase(NewTicketPurchaseReqDTO request) {
        }
    }
}
