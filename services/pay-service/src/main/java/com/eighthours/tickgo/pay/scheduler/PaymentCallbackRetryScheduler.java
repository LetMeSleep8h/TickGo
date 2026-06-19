package com.eighthours.tickgo.pay.scheduler;

import com.eighthours.tickgo.pay.service.PaymentCallbackTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCallbackRetryScheduler {

    private final PaymentCallbackTaskService paymentCallbackTaskService;

    @Scheduled(fixedDelay = 10000)
    public void retry() {
        paymentCallbackTaskService.retryTasks();
    }
}
