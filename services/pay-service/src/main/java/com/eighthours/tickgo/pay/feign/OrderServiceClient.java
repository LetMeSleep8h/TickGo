package com.eighthours.tickgo.pay.feign;

import com.eighthours.tickgo.pay.common.Result;
import com.eighthours.tickgo.pay.dto.OrderPayNotifyRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service-client", url = "http://localhost:8083")
public interface OrderServiceClient {

    @PostMapping("/order/pay-notify")
    Result<Void> notifyPaySuccess(@RequestBody OrderPayNotifyRequestDTO request);
}
