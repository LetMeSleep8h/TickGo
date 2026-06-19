package com.eighthours.tickgo.order.scheduler;

import com.eighthours.tickgo.order.service.CompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompensationScheduler {

    private final CompensationService compensationService;

    @Scheduled(fixedDelay = 10000)
    public void retryCompensationTasks() {
        try {
            compensationService.retryCompensationTasks();
        } catch (Exception e) {
            log.error("执行补偿任务调度失败", e);
        }
    }

}
