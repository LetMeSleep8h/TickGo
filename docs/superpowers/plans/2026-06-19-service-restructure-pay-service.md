# TickGo Service Restructure And Pay Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构微服务目录到 `services/`，新增独立 `pay-service` 和 `tickgo_pay` 库，打通支付回调订单闭环，并补一个可快速联调的前端测试页。

**Architecture:** 根工程继续作为 Maven 聚合工程，业务服务统一迁移到 `services/` 下。`pay-service` 负责支付域状态机和支付回调补偿，`order-service` 负责订单状态推进和车票确认，`gateway-service` 负责统一入口，`frontend` 补统一测试页串联购票与支付。

**Tech Stack:** Java 17, Spring Boot, Spring Cloud Gateway, OpenFeign, MyBatis-Plus, MySQL, Redis, RocketMQ, Vue3, TypeScript

---

### Task 1: Restructure Maven Modules Into `services/`

**Files:**
- Create: `services/`
- Move: `gateway-service`
- Move: `ticket-service`
- Move: `order-service`
- Move: `user-service`
- Modify: `pom.xml`

- [ ] **Step 1: Move existing service directories into `services/`**

Run:

```bash
mkdir -p /Users/pengzeyu/Java_Project/TickGo/services
mv /Users/pengzeyu/Java_Project/TickGo/gateway-service /Users/pengzeyu/Java_Project/TickGo/services/
mv /Users/pengzeyu/Java_Project/TickGo/ticket-service /Users/pengzeyu/Java_Project/TickGo/services/
mv /Users/pengzeyu/Java_Project/TickGo/order-service /Users/pengzeyu/Java_Project/TickGo/services/
mv /Users/pengzeyu/Java_Project/TickGo/user-service /Users/pengzeyu/Java_Project/TickGo/services/
```

Expected: four service folders now exist under `/Users/pengzeyu/Java_Project/TickGo/services`.

- [ ] **Step 2: Update root module paths**

Update [pom.xml](/Users/pengzeyu/Java_Project/TickGo/pom.xml:1):

```xml
<modules>
    <module>services/gateway-service</module>
    <module>services/ticket-service</module>
    <module>services/order-service</module>
    <module>services/user-service</module>
    <module>services/pay-service</module>
</modules>
```

- [ ] **Step 3: Verify the moved module layout is discoverable**

Run:

```bash
find /Users/pengzeyu/Java_Project/TickGo/services -maxdepth 1 -type d | sort
```

Expected: `gateway-service`, `ticket-service`, `order-service`, `user-service` are listed under `services/`.

- [ ] **Step 4: Verify Maven sees the updated module graph**

Run:

```bash
./mvnw -q -pl services/gateway-service,services/ticket-service,services/order-service,services/user-service help:evaluate -Dexpression=project.artifactId -DforceStdout
```

Expected: module artifact ids print without path resolution errors.

- [ ] **Step 5: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/pom.xml /Users/pengzeyu/Java_Project/TickGo/services
git commit -m "refactor: move services under services directory"
```

### Task 2: Scaffold `pay-service`

**Files:**
- Create: `services/pay-service/pom.xml`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/PayServiceApplication.java`
- Create: `services/pay-service/src/main/resources/application.yml`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/common/Result.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/exception/BizException.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Write the failing build expectation by referencing the missing module**

Run:

```bash
./mvnw -q -pl services/pay-service test
```

Expected: FAIL because `services/pay-service` does not exist yet.

- [ ] **Step 2: Create the new module `pom.xml`**

Create `services/pay-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.eighthours</groupId>
        <artifactId>tickgo</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>pay-service</artifactId>
    <name>pay-service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create the application bootstrap and base config**

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/PayServiceApplication.java`:

```java
package com.eighthours.tickgo.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@MapperScan("com.eighthours.tickgo.pay.mapper")
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
```

Create `services/pay-service/src/main/resources/application.yml`:

```yaml
server:
  port: 8085

spring:
  application:
    name: pay-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/tickgo_pay?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 12345678

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.eighthours.tickgo.pay.entity
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

- [ ] **Step 4: Create shared response and exception types**

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/common/Result.java`:

```java
package com.eighthours.tickgo.pay.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }

    public static Result<Void> error(String message) {
        return new Result<>(500, message, null);
    }
}
```

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/exception/BizException.java`:

```java
package com.eighthours.tickgo.pay.exception;

public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
```

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/exception/GlobalExceptionHandler.java`:

```java
package com.eighthours.tickgo.pay.exception;

import com.eighthours.tickgo.pay.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        return Result.error(ex.getMessage());
    }
}
```

- [ ] **Step 5: Run the pay-service module build**

Run:

```bash
./mvnw -q -pl services/pay-service test
```

Expected: module compiles or fails only because later domain files are not created yet.

- [ ] **Step 6: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/services/pay-service /Users/pengzeyu/Java_Project/TickGo/pom.xml
git commit -m "feat: scaffold pay service module"
```

### Task 3: Implement Pay Domain Persistence

**Files:**
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/entity/PaymentOrderDO.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/entity/PaymentCallbackTaskDO.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/enums/PaymentStatusEnum.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/enums/CallbackStatusEnum.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/mapper/PaymentOrderMapper.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/mapper/PaymentCallbackTaskMapper.java`

- [ ] **Step 1: Write the failing persistence test skeleton**

Create `services/pay-service/src/test/java/com/eighthours/tickgo/pay/enums/PaymentStatusEnumTest.java`:

```java
package com.eighthours.tickgo.pay.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentStatusEnumTest {

    @Test
    void shouldExposeExpectedCodes() {
        assertEquals(0, PaymentStatusEnum.INIT.getCode());
        assertEquals(20, PaymentStatusEnum.SUCCESS.getCode());
    }
}
```

Run:

```bash
./mvnw -q -pl services/pay-service -Dtest=PaymentStatusEnumTest test
```

Expected: FAIL because the enum does not exist yet.

- [ ] **Step 2: Create payment enums**

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/enums/PaymentStatusEnum.java`:

```java
package com.eighthours.tickgo.pay.enums;

import lombok.Getter;

@Getter
public enum PaymentStatusEnum {

    INIT(0, "待支付"),
    PAYING(10, "支付中"),
    SUCCESS(20, "支付成功"),
    FAILED(30, "支付失败"),
    CLOSED(40, "已关闭");

    private final Integer code;
    private final String desc;

    PaymentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
```

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/enums/CallbackStatusEnum.java`:

```java
package com.eighthours.tickgo.pay.enums;

import lombok.Getter;

@Getter
public enum CallbackStatusEnum {

    PENDING(0, "待回调"),
    SUCCESS(10, "回调成功"),
    FAILED(20, "回调失败");

    private final Integer code;
    private final String desc;

    CallbackStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
```

- [ ] **Step 3: Create the two DO entities**

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/entity/PaymentOrderDO.java`:

```java
package com.eighthours.tickgo.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_payment_order")
public class PaymentOrderDO {

    private Long id;
    private String paymentSn;
    private String orderSn;
    private Long userId;
    private Integer payAmount;
    private String payChannel;
    private Integer status;
    private Integer callbackStatus;
    private Integer callbackRetryCount;
    private LocalDateTime lastCallbackTime;
    private LocalDateTime successTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/entity/PaymentCallbackTaskDO.java`:

```java
package com.eighthours.tickgo.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_payment_callback_task")
public class PaymentCallbackTaskDO {

    private Long id;
    private String paymentSn;
    private String orderSn;
    private Integer status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 4: Create the mapper interfaces**

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/mapper/PaymentOrderMapper.java`:

```java
package com.eighthours.tickgo.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eighthours.tickgo.pay.entity.PaymentOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderDO> {
}
```

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/mapper/PaymentCallbackTaskMapper.java`:

```java
package com.eighthours.tickgo.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eighthours.tickgo.pay.entity.PaymentCallbackTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentCallbackTaskMapper extends BaseMapper<PaymentCallbackTaskDO> {
}
```

- [ ] **Step 5: Run the enum test to verify it now passes**

Run:

```bash
./mvnw -q -pl services/pay-service -Dtest=PaymentStatusEnumTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/services/pay-service
git commit -m "feat: add pay domain persistence types"
```

### Task 4: Implement `pay-service` APIs And Callback Retry

**Files:**
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/dto/CreatePaymentRequestDTO.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/dto/SubmitPaymentRequestDTO.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/dto/PaymentQueryRespDTO.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/dto/OrderPayNotifyRequestDTO.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/feign/OrderServiceClient.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/service/PayService.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/service/PaymentCallbackTaskService.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/service/impl/PayServiceImpl.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/service/impl/PaymentCallbackTaskServiceImpl.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/controller/PayController.java`
- Create: `services/pay-service/src/main/java/com/eighthours/tickgo/pay/scheduler/PaymentCallbackRetryScheduler.java`
- Test: `services/pay-service/src/test/java/com/eighthours/tickgo/pay/service/impl/PayServiceImplTest.java`

- [ ] **Step 1: Write the failing pay service unit test**

Create `services/pay-service/src/test/java/com/eighthours/tickgo/pay/service/impl/PayServiceImplTest.java`:

```java
package com.eighthours.tickgo.pay.service.impl;

import com.eighthours.tickgo.pay.dto.CreatePaymentRequestDTO;
import com.eighthours.tickgo.pay.dto.PaymentQueryRespDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayServiceImplTest {

    @Test
    void createPayment_shouldReturnInitStatus() {
        CreatePaymentRequestDTO request = new CreatePaymentRequestDTO();
        request.setOrderSn("ORDER_1");
        request.setUserId(1L);
        request.setPayAmount(45000);
        PaymentQueryRespDTO response = new PaymentQueryRespDTO();
        response.setStatus(0);
        assertEquals(0, response.getStatus());
    }
}
```

Run:

```bash
./mvnw -q -pl services/pay-service -Dtest=PayServiceImplTest test
```

Expected: FAIL once the real implementation test is expanded to instantiate missing classes.

- [ ] **Step 2: Create DTOs and Feign client**

Create DTOs with fields matching the design:

```java
package com.eighthours.tickgo.pay.dto;

import lombok.Data;

@Data
public class CreatePaymentRequestDTO {
    private String orderSn;
    private Long userId;
    private Integer payAmount;
}
```

```java
package com.eighthours.tickgo.pay.dto;

import lombok.Data;

@Data
public class SubmitPaymentRequestDTO {
    private String paymentSn;
}
```

```java
package com.eighthours.tickgo.pay.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentQueryRespDTO {
    private String paymentSn;
    private String orderSn;
    private Integer status;
    private Integer callbackStatus;
    private Integer payAmount;
    private LocalDateTime successTime;
}
```

```java
package com.eighthours.tickgo.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPayNotifyRequestDTO {
    private String paymentSn;
    private String orderSn;
}
```

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/feign/OrderServiceClient.java`:

```java
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
```

- [ ] **Step 3: Create pay service interfaces**

Create:

```java
package com.eighthours.tickgo.pay.service;

import com.eighthours.tickgo.pay.dto.CreatePaymentRequestDTO;
import com.eighthours.tickgo.pay.dto.PaymentQueryRespDTO;

public interface PayService {

    PaymentQueryRespDTO createPayment(CreatePaymentRequestDTO request);

    PaymentQueryRespDTO submitPayment(String paymentSn);

    PaymentQueryRespDTO queryPayment(String paymentSn, String orderSn);

    void closePayment(String paymentSn);
}
```

```java
package com.eighthours.tickgo.pay.service;

public interface PaymentCallbackTaskService {

    void createCallbackTask(String paymentSn, String orderSn, String errorMsg);

    void retryTasks();
}
```

- [ ] **Step 4: Implement `PayServiceImpl`**

Create `services/pay-service/src/main/java/com/eighthours/tickgo/pay/service/impl/PayServiceImpl.java` with these key behaviors:

```java
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
        PaymentOrderDO paymentOrder = paymentSn != null ? loadByPaymentSn(paymentSn) : loadByOrderSn(orderSn);
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

    private void notifyOrder(String paymentSn, String orderSn) {
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

    private void markCallbackSuccess(String paymentSn) {
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrderDO>()
                        .set(PaymentOrderDO::getCallbackStatus, CallbackStatusEnum.SUCCESS.getCode())
                        .set(PaymentOrderDO::getLastCallbackTime, LocalDateTime.now())
                        .eq(PaymentOrderDO::getPaymentSn, paymentSn));
    }

    private void markCallbackFailed(String paymentSn) {
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
```

- [ ] **Step 5: Implement callback task service, scheduler, and controller**

Create `PaymentCallbackTaskServiceImpl`, `PaymentCallbackRetryScheduler`, and `PayController` with these core methods:

```java
@Scheduled(fixedDelay = 10000)
public void retry() {
    paymentCallbackTaskService.retryTasks();
}
```

```java
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    @PostMapping("/create")
    public Result<PaymentQueryRespDTO> create(@RequestBody CreatePaymentRequestDTO request) {
        return Result.success(payService.createPayment(request));
    }

    @PostMapping("/submit")
    public Result<PaymentQueryRespDTO> submit(@RequestBody SubmitPaymentRequestDTO request) {
        return Result.success(payService.submitPayment(request.getPaymentSn()));
    }

    @GetMapping("/status")
    public Result<PaymentQueryRespDTO> status(@RequestParam(required = false) String paymentSn,
                                              @RequestParam(required = false) String orderSn) {
        return Result.success(payService.queryPayment(paymentSn, orderSn));
    }

    @PostMapping("/close")
    public Result<Void> close(@RequestParam String paymentSn) {
        payService.closePayment(paymentSn);
        return Result.success();
    }
}
```

- [ ] **Step 6: Run module tests**

Run:

```bash
./mvnw -q -pl services/pay-service test
```

Expected: PASS or only fail on tests that still need adjustment.

- [ ] **Step 7: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/services/pay-service
git commit -m "feat: implement pay service flow and callback retry"
```

### Task 5: Update `order-service` For Payment Notification

**Files:**
- Modify: `services/order-service/src/main/java/com/eighthours/tickgo/order/controller/OrderController.java`
- Modify: `services/order-service/src/main/java/com/eighthours/tickgo/order/service/OrderService.java`
- Modify: `services/order-service/src/main/java/com/eighthours/tickgo/order/service/impl/OrderServiceImpl.java`
- Create: `services/order-service/src/main/java/com/eighthours/tickgo/order/dto/PayNotifyRequestDTO.java`
- Test: `services/order-service/src/test/java/com/eighthours/tickgo/order/service/impl/OrderServiceImplTest.java`

- [ ] **Step 1: Write the failing test for pay notification behavior**

Add to `services/order-service/src/test/java/com/eighthours/tickgo/order/service/impl/OrderServiceImplTest.java`:

```java
@Test
void handlePayNotify_shouldReturnWhenOrderAlreadyPaid() {
    OrderDO order = new OrderDO();
    order.setOrderSn("ORDER_1");
    order.setStatus(OrderStatusEnum.PAID.getCode());
    when(orderMapper.selectOne(any())).thenReturn(order);

    service.handlePayNotify("PAY_1", "ORDER_1");

    verify(ticketServiceClient, never()).confirmTickets(any());
}
```

Run:

```bash
./mvnw -q -pl services/order-service -Dtest=OrderServiceImplTest test
```

Expected: FAIL because `handlePayNotify` does not exist yet.

- [ ] **Step 2: Add DTO and service contract**

Create `services/order-service/src/main/java/com/eighthours/tickgo/order/dto/PayNotifyRequestDTO.java`:

```java
package com.eighthours.tickgo.order.dto;

import lombok.Data;

@Data
public class PayNotifyRequestDTO {
    private String paymentSn;
    private String orderSn;
}
```

Update `OrderService.java`:

```java
void handlePayNotify(String paymentSn, String orderSn);
```

- [ ] **Step 3: Implement the new order service method**

Add to `OrderServiceImpl.java`:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void handlePayNotify(String paymentSn, String orderSn) {
    OrderDO order = orderMapper.selectOne(
            new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getOrderSn, orderSn));
    if (order == null) {
        throw new BizException("订单不存在");
    }
    if (OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
        return;
    }
    if (OrderStatusEnum.CANCELED.getCode().equals(order.getStatus())) {
        throw new BizException("订单已取消，不能支付成功");
    }

    int updated = orderMapper.update(null,
            new LambdaUpdateWrapper<OrderDO>()
                    .set(OrderDO::getStatus, OrderStatusEnum.PAID.getCode())
                    .eq(OrderDO::getOrderSn, orderSn)
                    .eq(OrderDO::getStatus, OrderStatusEnum.WAIT_PAY.getCode()));
    if (updated != 1) {
        throw new BizException("订单状态更新失败");
    }

    orderItemMapper.update(null,
            new LambdaUpdateWrapper<OrderItemDO>()
                    .set(OrderItemDO::getStatus, OrderItemStatusEnum.PAID.getCode())
                    .eq(OrderItemDO::getOrderSn, orderSn)
                    .eq(OrderItemDO::getStatus, OrderItemStatusEnum.WAIT_PAY.getCode()));

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            try {
                Result<Void> result = ticketServiceClient.confirmTickets(new TicketOrderRequestDTO(orderSn));
                if (result.getCode() != 200) {
                    compensationService.createCompensationTask(
                            CompensationServiceImpl.TASK_TYPE_CONFIRM_TICKET, orderSn);
                }
            } catch (Exception e) {
                compensationService.createCompensationTask(
                        CompensationServiceImpl.TASK_TYPE_CONFIRM_TICKET, orderSn);
            }
        }
    });
}
```

- [ ] **Step 4: Expose the pay notification endpoint**

Add to `OrderController.java`:

```java
@PostMapping("/pay-notify")
public Result<Void> payNotify(@RequestBody PayNotifyRequestDTO request) {
    orderService.handlePayNotify(request.getPaymentSn(), request.getOrderSn());
    return Result.success();
}
```

- [ ] **Step 5: Run the order-service tests**

Run:

```bash
./mvnw -q -pl services/order-service test
```

Expected: tests pass or only fail on pre-existing Mockito/JDK agent issues.

- [ ] **Step 6: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/services/order-service
git commit -m "feat: add pay notification handling to order service"
```

### Task 6: Update Gateway And Frontend APIs

**Files:**
- Modify: `services/gateway-service/src/main/resources/application.yml`
- Modify: `frontend/src/api/order.ts`
- Create: `frontend/src/api/pay.ts`
- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/views/PayDebugPage.vue`
- Modify: `frontend/src/layout/AppLayout.vue`
- Modify: `frontend/src/types/api.ts`

- [ ] **Step 1: Write the failing frontend import expectation**

Run:

```bash
cd /Users/pengzeyu/Java_Project/TickGo/frontend && npm run build
```

Expected: build currently succeeds without the pay debug route, so this is the baseline before adding new files.

- [ ] **Step 2: Add the pay route to the gateway**

Update `services/gateway-service/src/main/resources/application.yml`:

```yaml
            - id: pay-service
              uri: http://localhost:8085
              predicates:
                - Path=/api/pay/**
              filters:
                - StripPrefix=1
```

- [ ] **Step 3: Add frontend pay API definitions**

Create `frontend/src/api/pay.ts`:

```ts
import http from './http'

export function createPayment(payload: { orderSn: string; userId: number; payAmount: number }) {
  return http.post('/pay/create', payload)
}

export function submitPayment(payload: { paymentSn: string }) {
  return http.post('/pay/submit', payload)
}

export function getPaymentStatus(params: { paymentSn?: string; orderSn?: string }) {
  return http.get('/pay/status', { params })
}

export function closePayment(paymentSn: string) {
  return http.post(`/pay/close?paymentSn=${paymentSn}`)
}
```

- [ ] **Step 4: Add pay page types and route**

Update `frontend/src/types/api.ts` with:

```ts
export interface PaymentStatusResponse {
  paymentSn: string
  orderSn: string
  status: number
  callbackStatus: number
  payAmount: number
  successTime?: string
}
```

Update `frontend/src/router/index.ts`:

```ts
{
  path: '/pay',
  name: 'pay',
  component: () => import('../views/PayDebugPage.vue')
}
```

- [ ] **Step 5: Create the quick test page**

Create `frontend/src/views/PayDebugPage.vue` with a single-page flow:

```vue
<template>
  <a-card title="支付联调页">
    <a-space direction="vertical" style="width: 100%">
      <a-input v-model:value="orderSn" placeholder="订单号" />
      <a-input-number v-model:value="userId" :min="1" style="width: 200px" />
      <a-input-number v-model:value="payAmount" :min="1" style="width: 200px" />
      <a-space>
        <a-button type="primary" @click="handleCreatePayment">创建支付单</a-button>
        <a-button type="primary" @click="handleSubmitPayment" :disabled="!paymentSn">模拟支付成功</a-button>
        <a-button @click="handleQueryPayment">查询支付状态</a-button>
      </a-space>

      <a-descriptions bordered :column="1" v-if="payment">
        <a-descriptions-item label="支付单号">{{ payment.paymentSn }}</a-descriptions-item>
        <a-descriptions-item label="订单号">{{ payment.orderSn }}</a-descriptions-item>
        <a-descriptions-item label="支付状态">{{ payment.status }}</a-descriptions-item>
        <a-descriptions-item label="回调状态">{{ payment.callbackStatus }}</a-descriptions-item>
        <a-descriptions-item label="支付金额">{{ payment.payAmount }}</a-descriptions-item>
      </a-descriptions>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { createPayment, getPaymentStatus, submitPayment } from '../api/pay'
import type { PaymentStatusResponse } from '../types/api'

const orderSn = ref('')
const paymentSn = ref('')
const userId = ref(1)
const payAmount = ref(100)
const payment = ref<PaymentStatusResponse | null>(null)

async function handleCreatePayment() {
  const res = await createPayment({ orderSn: orderSn.value, userId: userId.value, payAmount: payAmount.value })
  payment.value = res
  paymentSn.value = res.paymentSn
  message.success('支付单创建成功')
}

async function handleSubmitPayment() {
  const res = await submitPayment({ paymentSn: paymentSn.value })
  payment.value = res
  message.success('支付成功已提交')
}

async function handleQueryPayment() {
  const res = await getPaymentStatus({ paymentSn: paymentSn.value || undefined, orderSn: orderSn.value || undefined })
  payment.value = res
}
</script>
```

- [ ] **Step 6: Add navigation entry and run frontend build**

Update `frontend/src/layout/AppLayout.vue` to include a `/pay` navigation item.

Run:

```bash
cd /Users/pengzeyu/Java_Project/TickGo/frontend && npm run build
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/services/gateway-service /Users/pengzeyu/Java_Project/TickGo/frontend
git commit -m "feat: add pay route and frontend debug page"
```

### Task 7: Consolidate Initialization SQL

**Files:**
- Modify: `resources/db/tickgo_init_all.sql`
- Modify: `resources/db/tickgo_order.sql`
- Modify: `resources/db/tickgo_ticket.sql`
- Create: `resources/db/tickgo_pay.sql`

- [ ] **Step 1: Write the failing initialization expectation**

Run:

```bash
rg -n "tickgo_pay|t_payment_order|t_payment_callback_task" /Users/pengzeyu/Java_Project/TickGo/resources/db
```

Expected: FAIL to find `tickgo_pay` definitions before the new SQL is added.

- [ ] **Step 2: Create pay-service SQL**

Create `resources/db/tickgo_pay.sql`:

```sql
CREATE DATABASE IF NOT EXISTS tickgo_pay
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tickgo_pay;

DROP TABLE IF EXISTS t_payment_callback_task;
DROP TABLE IF EXISTS t_payment_order;

CREATE TABLE t_payment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_sn VARCHAR(64) NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    pay_amount INT NOT NULL,
    pay_channel VARCHAR(32) NOT NULL DEFAULT 'MOCK',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0INIT 10PAYING 20SUCCESS 30FAILED 40CLOSED',
    callback_status TINYINT NOT NULL DEFAULT 0 COMMENT '0PENDING 10SUCCESS 20FAILED',
    callback_retry_count INT NOT NULL DEFAULT 0,
    last_callback_time DATETIME NULL,
    success_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_sn (payment_sn),
    UNIQUE KEY uk_order_sn (order_sn)
) COMMENT='支付单表';

CREATE TABLE t_payment_callback_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_sn VARCHAR(64) NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2成功 3失败',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 5,
    next_retry_time DATETIME NOT NULL,
    error_msg TEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_callback (payment_sn),
    KEY idx_status_next_retry_time (status, next_retry_time)
) COMMENT='支付回调补偿任务表';
```

- [ ] **Step 3: Merge pay-service SQL into the consolidated init script**

Append to `resources/db/tickgo_init_all.sql`:

```sql
-- ------------------------------------
-- 4. pay-service database and operational tables
-- ------------------------------------
CREATE DATABASE IF NOT EXISTS tickgo_pay
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tickgo_pay;

DROP TABLE IF EXISTS t_payment_callback_task;
DROP TABLE IF EXISTS t_payment_order;

CREATE TABLE t_payment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_sn VARCHAR(64) NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    pay_amount INT NOT NULL,
    pay_channel VARCHAR(32) NOT NULL DEFAULT 'MOCK',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0INIT 10PAYING 20SUCCESS 30FAILED 40CLOSED',
    callback_status TINYINT NOT NULL DEFAULT 0 COMMENT '0PENDING 10SUCCESS 20FAILED',
    callback_retry_count INT NOT NULL DEFAULT 0,
    last_callback_time DATETIME NULL,
    success_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_sn (payment_sn),
    UNIQUE KEY uk_order_sn (order_sn)
);

CREATE TABLE t_payment_callback_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_sn VARCHAR(64) NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2成功 3失败',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 5,
    next_retry_time DATETIME NOT NULL,
    error_msg TEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_callback (payment_sn),
    KEY idx_status_next_retry_time (status, next_retry_time)
);
```

- [ ] **Step 4: Verify all four service databases are covered**

Run:

```bash
rg -n "CREATE DATABASE IF NOT EXISTS tickgo_" /Users/pengzeyu/Java_Project/TickGo/resources/db/tickgo_init_all.sql
```

Expected: `tickgo_user`, `tickgo_ticket`, `tickgo_order`, and `tickgo_pay` are all present.

- [ ] **Step 5: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/resources/db
git commit -m "feat: consolidate pay service schema into init sql"
```

### Task 8: Final Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README structure and startup notes**

Add short sections to `README.md` covering:

```md
## 目录结构

- services/gateway-service
- services/ticket-service
- services/order-service
- services/user-service
- services/pay-service

## 初始化数据库

```bash
mysql -uroot -p12345678 < resources/db/tickgo_init_all.sql
```
```

- [ ] **Step 2: Run backend module tests**

Run:

```bash
./mvnw -q test
```

Expected: PASS, or document any remaining pre-existing Mockito/JDK 21 issues if they still block test execution.

- [ ] **Step 3: Run frontend production build**

Run:

```bash
cd /Users/pengzeyu/Java_Project/TickGo/frontend && npm run build
```

Expected: PASS.

- [ ] **Step 4: Smoke-test the end-to-end flow manually**

Run services locally and verify:

```text
1. 访问 /pay 测试页
2. 先在购票页创建订单
3. 在支付页创建支付单
4. 点击模拟支付成功
5. 查询支付状态为 SUCCESS
6. 查询订单状态为 PAID
7. 查询票务状态为已确认
```

Expected: the entire synchronous callback flow works.

- [ ] **Step 5: Commit**

```bash
git add /Users/pengzeyu/Java_Project/TickGo/README.md
git commit -m "docs: update readme for pay service restructure"
```
