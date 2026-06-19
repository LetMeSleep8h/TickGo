package com.eighthours.tickgo.order.service;

public interface CompensationService {

    void createCompensationTask(String taskType, String bizId);

    void retryCompensationTasks();

}
